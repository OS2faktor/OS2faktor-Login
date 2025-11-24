package dk.digitalidentity.common.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.NemloginQueue;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NemloginAction;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.dto.ChildDTO;
import dk.digitalidentity.common.service.dto.CprLookupDTO;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@EnableCaching
public class CprService {

	@Autowired
	private CommonConfiguration configuration;

	@Autowired
	private PersonService personService;
	
	@Autowired
	private AuditLogger auditLogger;
	
	@Autowired
	private NemloginQueueService nemloginQueueService;

	private CprService self;

	@Autowired
	public CprService(@Lazy CprService self) {
		this.self = self;
	}
	
	public boolean checkIsDead(Person person) {
		if (!configuration.getCpr().isEnabled()) {
			log.warn("Called method checkIsDead, but cpr is disabled");
			return false;
		}
		
		// robots are never dead
		if (person.isRobot()) {
			return false;
		}

		Future<CprLookupDTO> cprFuture = self.getByCpr(person.getCpr());
		CprLookupDTO dto = null;

		// the 5 second timeout is introduced to deal with potentially extremly long response times
		// from the Serviceplatform at times. We will skip lookup on that person this time around,
		// and perform the lookup on the following day. The exception is just logged, and not dealt
		// with in any other way, as timeouts on the Serviceplatform is quite common
		try {
			dto = (cprFuture != null) ? cprFuture.get(5, TimeUnit.SECONDS) : null;
		}
		catch (InterruptedException | ExecutionException | TimeoutException e) {
			auditLogger.checkPersonIsDead(person, null);
			log.warn("Cpr lookup failed for person with id " + person.getId());
			return false;
		}

		if (dto == null) {
			auditLogger.checkPersonIsDead(person, null);
			log.warn("Cpr response was empty for person with id " + person.getId());
			return false;
		}
		
		// TODO: if the person does not exists in CPR, but has NSIS-allowed, we should probably auditlog
		//       this as something the municipality should look into
		if (dto.isDoesNotExist()) {
			auditLogger.checkPersonIsDead(person, null);
			return false;
		}
		
		auditLogger.checkPersonIsDead(person, dto.isDead());

		// update name of changed
		if (updateName(person, dto)) {
			personService.save(person);
		}

		return dto.isDead();
	}

	@Async
	public Future<CprLookupDTO> getByCpr(String cpr) {
		if (!configuration.getCpr().isEnabled()) {
			log.warn("Called method getByCpr, but cpr is disabled");
			return null;
		}

		RestTemplate restTemplate = new RestTemplate();
		// no reason to lookup invalid cpr numbers
		if (!validCpr(cpr) || isFictionalCpr(cpr)) {
			return null;
		}

		String cprResourceUrl = configuration.getCpr().getUrl();
		if (!cprResourceUrl.endsWith("/")) {
			cprResourceUrl += "/";
		}
		cprResourceUrl += "api/person?cpr=" + cpr + "&cvr=" + configuration.getCustomer().getCvr();

		try {
			ResponseEntity<CprLookupDTO> response = restTemplate.getForEntity(cprResourceUrl, CprLookupDTO.class);
			return CompletableFuture.completedFuture(response.getBody());
		}
		catch (IllegalArgumentException ex) {
			log.warn("Failed to lookup: " + safeCprSubstring(cpr), ex);

			return null;
		}
		catch (RestClientResponseException ex) {
			String responseBody = ex.getResponseBodyAsString();

			if (ex.getStatusCode().value() == 404 && responseBody != null && responseBody.contains("PNR not found")) {
				log.warn("Person cpr does not exists in cpr-register: " + safeCprSubstring(cpr));
				
				CprLookupDTO dto = new CprLookupDTO();
				dto.setDoesNotExist(true);
				return CompletableFuture.completedFuture(dto);
			}
			else {
				log.warn("Failed to lookup: " + safeCprSubstring(cpr), ex);
			}

			return null;
		}
	}

	@CacheEvict(value = "getChildren", allEntries = true)
	public void cleanChildrenCache() {
		;
	}

	@Cacheable("getChildren")
	public List<Person> getChildrenPasswordAllowed(String cpr) {
		List<Person> result = new ArrayList<>();
		
		Future<CprLookupDTO> cprFuture = getByCpr(cpr);
		CprLookupDTO personLookup = null;

		try {
			personLookup = (cprFuture != null) ? cprFuture.get(5, TimeUnit.SECONDS) : null;
		}
		catch (InterruptedException | ExecutionException | TimeoutException ex) {
			log.warn("Got a timeout on lookup of children", ex);
			return result;
		}

		if (personLookup != null && personLookup.getChildren() != null && !personLookup.getChildren().isEmpty()) {
			for (ChildDTO child : personLookup.getChildren()) {
				List<Person> childPersons = personService.getByCpr(child.getCpr());

				for (Person person : childPersons) {
					if (person.isNsisAllowed()) {
						continue;
					}
					
					if (person.isLocked()) {
						continue;
					}
					
					if (isAdult(getBirthDateFromCpr(person.getCpr()))) {
						continue;
					}
					
					result.add(person);
				}
			}
		}

		return result;
	}

	private boolean isAdult(LocalDate birthday) {
		return LocalDate.from(birthday).until(LocalDate.now(), ChronoUnit.YEARS) >= 16;
	}

	private LocalDate getBirthDateFromCpr(String cpr) {
		var datePart = Integer.parseInt(cpr.substring(0, 2));
		var monthPart = Integer.parseInt(cpr.substring(2, 4));
		var yearPart = Integer.parseInt(cpr.substring(4, 6));
		var seventh = Integer.parseInt(cpr.substring(6, 7));
		var century = 0;
		
		if (seventh < 4) {
			century = 1900;
		}
		else if (seventh == 4 || seventh == 9) {
			century = yearPart < 37 ? 2000 : 1900;
		}
		else {
			century = yearPart < 58 ? 2000 : 1800;
		}
		
		return LocalDate.of(century + yearPart, monthPart, datePart);
	}
	
	public static String safeCprSubstring(String cpr) {
		if (cpr.length() >= 6) {
			return cpr.substring(0, 6) + "-XXXX";
		}
		
		return cpr;
	}
	
	public boolean validCpr(String cpr) {
		if (cpr == null || cpr.length() != 10) {
			return false;
		}
		
		for (char c : cpr.toCharArray()) {
			if (!Character.isDigit(c)) {
				return false;
			}
		}
		
		int days = Integer.parseInt(cpr.substring(0, 2));
		int month = Integer.parseInt(cpr.substring(2, 4));

		if (days < 1 || days > 31) {
			return false;
		}

		if (month < 1 || month > 12) {
			return false;
		}

		return true;
	}
	
	public boolean isFictionalCpr(String cpr) {
		try {
			LocalDate.parse(cpr.substring(0, 6), DateTimeFormatter.ofPattern("ddMMyy"));
		}
		catch (DateTimeParseException ex) {
			return true;
		}

		return false;
	}
	
	public boolean updateName(Person person, CprLookupDTO dto) {
		boolean change = false;
		
		if (StringUtils.hasLength(dto.getFirstname()) && StringUtils.hasLength(dto.getLastname())) {
			String updatedName = dto.getFirstname() + " " + dto.getLastname();
			
			if (!Objects.equals(person.getName(), updatedName)) {
				person.setName(updatedName);
				
				auditLogger.updateNameFromCpr(person);

				if (StringUtils.hasLength(person.getNemloginUserUuid())) {
					NemloginQueue queue = new NemloginQueue();
					queue.setAction(NemloginAction.UPDATE_PROFILE_ONLY);
					queue.setPerson(person);
					queue.setTts(LocalDateTime.now());

					nemloginQueueService.save(queue);
				}
				
				change = true;
			}
			
			if (!Objects.equals(person.isNameProtected(), dto.isAddressProtected())) {
				person.setNameProtected(dto.isAddressProtected());
				change = true;
			}
			
			if (!person.isCprNameUpdated()) {
				person.setCprNameUpdated(true);
				change = true;
			}
		}

		return change;
	}
}
