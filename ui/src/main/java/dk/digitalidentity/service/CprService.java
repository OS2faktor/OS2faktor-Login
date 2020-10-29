package dk.digitalidentity.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.config.OS2faktorConfiguration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CprService {

	@Autowired
	private OS2faktorConfiguration configuration;

	@Autowired
	private PersonService personService;

	@Transactional // (rollbackFor = Exception.class) No we do not want to rollback the successful lookups
	public void syncNamesWithCprTask() throws Exception {

		// Change list of people to a map mapped by cpr
		List<Person> all = personService.getAll();
		HashMap<String, List<Person>> personMap = new HashMap<>();
		for (Person person : all) {
			if (!personMap.containsKey(person.getCpr())) {
				personMap.put(person.getCpr(), new ArrayList<Person>());
			}

			personMap.get(person.getCpr()).add(person);
		}

		// Run through all unique CPR numbers and call CPR to check for changes
		int failedAttempts = 0;

		List<Person> toBeSaved = new ArrayList<>();
		for (String cpr : personMap.keySet()) {
			if (failedAttempts >= 3) {
	                        log.error("Got 3 timeouts in a row - aborting further lookup");
				break;
			}

			if (cpr.length() == 10) {
				try {
					// Fetch information from CPR
					Future<CprLookupDTO> cprFuture = getByCpr(cpr);
					CprLookupDTO dto = (cprFuture != null) ? cprFuture.get(5, TimeUnit.SECONDS) : null;

					if (dto == null) {
						log.warn("Cpr response was empty");
						continue;
					}

					// Extra check
					if (StringUtils.isEmpty(dto.getFirstname()) || StringUtils.isEmpty(dto.getLastname())) {
						continue;
					}

					// Change name for updated people
					List<Person> people = personMap.get(cpr);
					for (Person person : people) {
						String updatedName = dto.getFirstname() + " " + dto.getLastname();

						if (!Objects.equals(person.getName(), updatedName)) {
							person.setName(updatedName);
							toBeSaved.add(person);
						}

					}

				} catch (TimeoutException ex) {
					log.warn("Could not fetch data from cpr within the timeout for person", ex);
					failedAttempts++;
					continue;
				}

				failedAttempts = 0;
			}
		}

		if (toBeSaved.size() > 0) {
			personService.saveAll(toBeSaved);
		}
	}

	@Async
	public Future<CprLookupDTO> getByCpr(String cpr) {
		RestTemplate restTemplate = new RestTemplate();
		// no reason to lookup invalid cpr numbers
		if (!validCpr(cpr)) {
			return null;
		}

		String cprResourceUrl = configuration.getCpr().getUrl();
		if (!cprResourceUrl.endsWith("/")) {
			cprResourceUrl += "/";
		}
		cprResourceUrl += "api/person?cpr=" + cpr + "&cvr=" + configuration.getCpr().getCvr();

		try {
			ResponseEntity<CprLookupDTO> response = restTemplate.getForEntity(cprResourceUrl, CprLookupDTO.class);
			return new AsyncResult<CprLookupDTO>(response.getBody());
		}
		catch (RestClientException ex) {
			log.warn("Failed to lookup: " + safeCprSubstring(cpr), ex);

			return null;
		}
	}

	public static String safeCprSubstring(String cpr) {
		if (cpr.length() >= 6) {
			return cpr.substring(0, 6) + "-XXXX";
		}
		
		return cpr;
	}
	
	private boolean validCpr(String cpr) {
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
}
