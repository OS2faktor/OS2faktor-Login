package dk.digitalidentity.nemlogin.service;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.http.conn.HttpHostConnectException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.EmailTemplate;
import dk.digitalidentity.common.dao.model.EmailTemplateChild;
import dk.digitalidentity.common.dao.model.MitIdErhvervAccountError;
import dk.digitalidentity.common.dao.model.MitidErhvervCache;
import dk.digitalidentity.common.dao.model.NemloginQueue;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.EmailTemplateType;
import dk.digitalidentity.common.dao.model.enums.MitIdErhvervAccountErrorType;
import dk.digitalidentity.common.dao.model.enums.NemloginAction;
import dk.digitalidentity.common.dao.model.enums.SettingKey;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.CprService;
import dk.digitalidentity.common.service.EmailTemplateService;
import dk.digitalidentity.common.service.MitIdErhvervAccountErrorService;
import dk.digitalidentity.common.service.NemloginQueueService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.SettingService;
import dk.digitalidentity.common.service.dto.CprLookupDTO;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.nemlogin.service.model.ActivationOrderRequest;
import dk.digitalidentity.nemlogin.service.model.Authenticator;
import dk.digitalidentity.nemlogin.service.model.AuthenticatorsResponse;
import dk.digitalidentity.nemlogin.service.model.CredentialsRequest;
import dk.digitalidentity.nemlogin.service.model.Employee;
import dk.digitalidentity.nemlogin.service.model.EmployeeChangeEmailRequest;
import dk.digitalidentity.nemlogin.service.model.EmployeeChangeLocalUserIdRequest;
import dk.digitalidentity.nemlogin.service.model.EmployeeCreateRequest;
import dk.digitalidentity.nemlogin.service.model.EmployeeCreateResponse;
import dk.digitalidentity.nemlogin.service.model.EmployeeSearchRequest;
import dk.digitalidentity.nemlogin.service.model.EmployeeSearchResponse;
import dk.digitalidentity.nemlogin.service.model.FullEmployee;
import dk.digitalidentity.nemlogin.service.model.IdentityAuthenticators;
import dk.digitalidentity.nemlogin.service.model.IdentityOrganizationProfile;
import dk.digitalidentity.nemlogin.service.model.IdentityProfile;
import dk.digitalidentity.nemlogin.service.model.TokenResponse;
import dk.digitalidentity.nemlogin.service.model.UpdateProfileRequest;
import dk.digitalidentity.service.EmailTemplateSenderService;
import dk.digitalidentity.service.MitidErhvervCacheService;
import lombok.extern.slf4j.Slf4j;

@EnableCaching
@EnableScheduling
@Slf4j
@Service
public class NemLoginService {
	private List<Employee> migratedEmployeesHolder = null;
	private long readAllIdentitiesFailureInARow = 0;
	
	@Qualifier("nemLoginRestTemplate")
	@Autowired
	private RestTemplate restTemplate;
	
	@Autowired
	private CommonConfiguration config;
	
	@Autowired
	private OS2faktorConfiguration os2faktorConfiguration;
	
	@Autowired
	private NemLoginService self;

	@Autowired
	private PersonService personService;
	
	@Autowired
	private NemloginQueueService nemloginQueueService;
	
	@Autowired
	private MitidErhvervCacheService mitidErhvervCacheService;
	
	@Autowired
	private EmailTemplateService emailTemplateService;
	
	@Autowired
	private EmailTemplateSenderService emailTemplateSenderService;

	@Autowired
	private AuditLogger auditLogger;

	@Autowired
	private MitIdErhvervAccountErrorService mitIdErhvervAccountErrorService;
	
	@Autowired
	private SettingService settingService;
	
	@Autowired
	private CprService cprService;

	@EventListener(ApplicationReadyEvent.class)
	public void runOnStartup() {
		if (config.getNemLoginApi().isEnabled() && os2faktorConfiguration.getScheduled().isEnabled()) {
			
			// migrate existing users if needed
			if (config.getNemLoginApi().isMigrateExistingUsers()) {
				migrateExistingNemloginUsers();
			}
			
			// migrate private MitID setting - ONCE - during startup
			if (config.getNemLoginApi().isPrivateMitIdEnabled() && !settingService.getBoolean(SettingKey.MIGRATED_PRIVATE_MITID)) {
				List<MitidErhvervCache> cache = mitidErhvervCacheService.findAll();
				
				// NemLog-in UUID should be unique, but there is no DB constraint, so we try to be safe
				Map<String, List<Person>> persons = personService.getAll().stream().collect(Collectors.groupingBy(Person::getNemloginUserUuid));

				for (MitidErhvervCache cacheEntry : cache) {
					if (cacheEntry.isMitidPrivatCredential()) {
						List<Person> personHits = persons.get(cacheEntry.getUuid());
						
						if (personHits != null && personHits.size() > 0) {
							for (Person person : personHits) {
								// this is a heavy operation if we do it 1000+ times, but we suspect the amount to be low, and
								// it IS a one-off, so no bulk saving this change
								person.setPrivateMitId(true);
								personService.save(person);
							}
						}
					}
				}

				settingService.setBoolean(SettingKey.MIGRATED_PRIVATE_MITID, true);
			}
		}
	}
	
	@Cacheable(value = "token", unless = "#result == null")
	public String fetchToken() {
		String url = config.getNemLoginApi().getBaseUrl() + "/api/administration/idmlogin/tls/authenticate";
		String accessToken = null;
		
		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json");
		
		HttpEntity<String> request = new HttpEntity<>(headers);
		
		try {
			ResponseEntity<TokenResponse> response = restTemplate.exchange(url, HttpMethod.POST, request, TokenResponse.class);

			if (response.getStatusCode().value() == 200 && response.getBody() != null) {
				accessToken = response.getBody().getAccessToken();
			}
			else {
				log.error("Failed to fetch token from nemloginApi - statusCode=" + response.getStatusCode().value());
			}
		}
		catch (Exception ex) {
			log.error("Failed to fetch token from nemloginApi", ex);
		}
		
		return accessToken;
	}
	
	@CacheEvict(value = "token", allEntries = true)
	public void cleanUpToken() {
		;
	}

	@Scheduled(fixedRate = 30 * 60 * 1000)
	public void cleanUpTask() {
		self.cleanUpToken();
	}

	@Transactional
	public void cleanupMitIDErhverv() {
		List<Person> personsInMitIDErhverv = personService.getByNemloginUserUuidNotNull();
		Map<String, Person> map = personsInMitIDErhverv.stream().collect(Collectors.toMap(Person::getNemloginUserUuid, Function.identity()));
		
		List<Employee> employees = getAllExistingEmployees(false);
		if (employees == null) {
			return;
		}

		for (Employee employee : employees) {
			
			// skip those we don't know			
			Person person = map.get(employee.getUuid());
			if (person == null) {
				continue;
			}
			
			boolean shouldBeActiveInMitIDErhverv = !person.isLocked() && person.isTransferToNemlogin();
			if ("Active".equals(employee.getStatus()) && !shouldBeActiveInMitIDErhverv) {
				log.info("Queueing suspend for MitID user: " + person.getId() + " / " + person.getSamaccountName());
				nemloginQueueService.save(new NemloginQueue(person, NemloginAction.SUSPEND));
			}
			else if ("Suspended".equals(employee.getStatus()) && shouldBeActiveInMitIDErhverv) {
				log.info("Queueing reactivate for MitID user: " + person.getId() + " / " + person.getSamaccountName());
				nemloginQueueService.save(new NemloginQueue(person, NemloginAction.REACTIVATE));
			}
		}
	}
	
	@Transactional
	public void sync() {
		if (config.getNemLoginApi().isMigrateExistingUsers()) {
			log.error("Will not perform sync - the application has started with migration enabled!");
			return;
		}

		// special hack - we want to support fetching from migrated users during sync (for UPDATE_PROFILE case),
		// at least in the beginning (code can be deleted later), but we don't want to read the full list for
		// each user, so we read it once (if needed at all), and store a copy - this is cleared on each new sync
		migratedEmployeesHolder = null;
		
		List<NemloginQueue> queues = nemloginQueueService.getAllNotFailed();
		List<NemloginQueue> toDelete = new ArrayList<>();

		for (NemloginQueue queue : queues) {			
			String failureReason = null;
			
			if (!queue.getAction().equals(NemloginAction.DELETE) && queue.getPerson().getDomain().getNemLoginUserSuffix() == null) {
				log.error("Missing NemLoginUserSuffix for  " + queue.getPerson().getId() + " in domain " + queue.getPerson().getDomain().getName() + " - skipping user!");
				continue;
			}

			switch (queue.getAction()) {
				case DELETE:
					failureReason = deleteEmployee(queue.getNemloginUserUuid());
					break;
				case CHANGE_EMAIL:
					failureReason = changeEmail(queue.getPerson());			
					break;
				case SUSPEND:
					failureReason = suspendEmployee(queue.getPerson());
					break;
				case REACTIVATE:
					failureReason = reactivateEmployee(queue.getPerson());
					break;
				case CREATE:
					failureReason = createEmployee(queue.getPerson());
					break;
				case UPDATE_PROFILE:
					failureReason = updateDraftProfile(queue.getPerson());
					break;
				case UPDATE_PROFILE_ONLY:
					failureReason = updateProfile(queue.getPerson());
					break;
				case ACTIVATE:
					failureReason = activateEmployee(queue.getPerson());
					break;
				case ASSIGN_LOCAL_USER_ID:
					failureReason = assignLocalUserId(queue.getPerson());
					break;
				case ASSIGN_PRIVATE_MIT_ID:
					failureReason = assignPrivateMitId(queue.getPerson());
					break;
				case REVOKE_PRIVATE_MIT_ID:
					failureReason = revokePrivateMitId(queue.getPerson());
					break;
			}

			if (failureReason == null) {
				toDelete.add(queue);
			}
			else {
				if (failureReason.length() > 250) {
					failureReason = failureReason.substring(0, 250);
				}

				queue.setFailed(true);
				queue.setFailureReason(failureReason);
				nemloginQueueService.save(queue);
			}
		}
		
		if (!toDelete.isEmpty()) {
			nemloginQueueService.deleteAll(toDelete);
		}
	}
	
	private String updateProfile(Person person) {
		if (person == null) {
			log.error("Will not update profile. Person was null");
			return "person is null";
		}

		log.info("Updating profile on person " + person.getId());
		
		if (!StringUtils.hasLength(person.getNemloginUserUuid())) {
			String nemloginUuid = fetchNemLoginUuid(person.getRid());
			if (nemloginUuid == null) {
				log.error("Will not update profile for person " + person.getId() + ". The person does not have a nemloginUserUuid");
				return "nemloginUserUuid is null";
			}
			
			person.setNemloginUserUuid(nemloginUuid);
		}
		
		if (!StringUtils.hasLength(person.getSamaccountName())) {
			log.error("Will not update profile for person " + person.getId() + ". The person does not have a sAMAccountName");
			return "sAMAccountName is not null";
		}

		String url = config.getNemLoginApi().getBaseUrl() + "/api/administration/identity/employee/" + person.getNemloginUserUuid() + "/profile";

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json");
		headers.add("Authorization", "Bearer " + self.fetchToken());

		UpdateProfileRequest body = new UpdateProfileRequest(person, config.getNemLoginApi().getDefaultEmail());

		HttpEntity<UpdateProfileRequest> request = new HttpEntity<>(body, headers);

		try {
			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, request, String.class);

			if (response.getStatusCode().value() >= 200 && response.getStatusCode().value() <= 299) {
				;
			}
			else {
				log.error("Failed to update profile on person with uuid " + person.getUuid() + ". Error: " + response.getBody());
				return "Error: " + response.getBody();
			}
		}
		catch (Exception ex) {
			log.error("Failed to update profile for person with uuid " + person.getUuid(), ex);
			return "Exception: " + ex.getMessage();
		}
				
		return null;
	}
	
	private String updateDraftProfile(Person person) {
		if (person == null) {
			log.error("Will not update draft profile. Person was null");
			return "person is null";
		}

		log.info("Updating draft profile on person " + person.getId());
		
		if (!StringUtils.hasLength(person.getNemloginUserUuid())) {
			String nemloginUuid = fetchNemLoginUuid(person.getRid());
			if (nemloginUuid == null) {
				log.error("Will not update draft profile for person " + person.getId() + ". The person does not have a nemloginUserUuid");
				return "nemloginUserUuid is null";
			}
			
			person.setNemloginUserUuid(nemloginUuid);
		}
		
		if (!StringUtils.hasLength(person.getSamaccountName())) {
			log.error("Will not update draft profile for person " + person.getId() + ". The person does not have a sAMAccountName");
			return "sAMAccountName is not null";
		}

		// step 0 - skal sikre at der er et CPR +folkeregisternavn på kontoen inden vi forsøger at aktivere, ellers går det grueligt galt
		String url = config.getNemLoginApi().getBaseUrl() + "/api/administration/identity/employee/" + person.getNemloginUserUuid() + "/profile/draft";

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json");
		headers.add("Authorization", "Bearer " + self.fetchToken());

		UpdateProfileRequest body = new UpdateProfileRequest(person, config.getNemLoginApi().getDefaultEmail());

		HttpEntity<UpdateProfileRequest> request = new HttpEntity<>(body, headers);

		try {
			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, request, String.class);

			if (response.getStatusCode().value() >= 200 && response.getStatusCode().value() <= 299) {
				;
			}
			else {
				log.error("Failed to update draft profile on person with uuid " + person.getUuid() + ". Error: " + response.getBody());
				return "Error: " + response.getBody();
			}
		}
		catch (Exception ex) {
			log.error("Failed to update draft profile for person with uuid " + person.getUuid(), ex);
			return "Exception: " + ex.getMessage();
		}
		
		// step 1 - create a new order for activation
		nemloginQueueService.save(new NemloginQueue(person, NemloginAction.ACTIVATE));
		
		return null;
	}
	
	private String fetchNemLoginUuid(String rid) {
		if (migratedEmployeesHolder == null) {
			migratedEmployeesHolder = getAllPendingMigratedEmployees();
		}
		
		if (migratedEmployeesHolder == null || migratedEmployeesHolder.size() == 0) {
			return null;
		}

		for (Employee employee : migratedEmployeesHolder) {
			if (Objects.equals(employee.getRid(), rid)) {
				return employee.getUuid();
			}
		}
		
		return null;
	}

	private String activateEmployee(Person person) {
		if (person == null) {
			log.error("Will not activate. Person was null");
			return "person is null";
		}

		log.info("Activating person " + person.getId());
		
		if (!StringUtils.hasLength(person.getNemloginUserUuid())) {
			log.error("Will not activate person " + person.getId() + ". The person does not have a nemloginUserUuid");
			return "nemloginUserUuid is null";
		}
		
		if (!StringUtils.hasLength(person.getSamaccountName())) {
			log.error("Will not activate for person " + person.getId() + ". The person does not have a sAMAccountName");
			return "sAMAccountName is not null";
		}

		// step 0 - create a "fake" activation order so we can do stuff
		String url = config.getNemLoginApi().getBaseUrl() + "/api/admin/organization-activation/activation-orders";

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json");
		headers.add("Authorization", "Bearer " + self.fetchToken());

		ActivationOrderRequest body = new ActivationOrderRequest();
		body.getUserUuids().add(person.getNemloginUserUuid());

		HttpEntity<ActivationOrderRequest> request = new HttpEntity<>(body, headers);

		try {
			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

			if (response.getStatusCode().value() >= 200 && response.getStatusCode().value() <= 299) {
				;
			}
			else {
				log.error("Failed to generate activation order for person with uuid " + person.getUuid() + ". Error: " + response.getBody());
				return "Error: " + response.getBody();
			}
		}
		catch (Exception ex) {
			log.error("Failed to generate activation order for person with uuid " + person.getUuid(), ex);
			return "Exception: " + ex.getMessage();
		}
		
		// step 1 - create assign userid order
		nemloginQueueService.save(new NemloginQueue(person, NemloginAction.ASSIGN_LOCAL_USER_ID));
		
		return null;
	}
	
	private String assignLocalUserId(Person person) {
		if (person == null) {
			log.error("Will not update localUserId. Person was null");
			return "person is null";
		}

		log.info("Updating localUserId on person " + person.getId());
		
		if (!StringUtils.hasLength(person.getNemloginUserUuid())) {
			log.error("Will not update localUserId for person " + person.getId() + ". The person does not have a nemloginUserUuid");
			return "nemloginUserUuid is null";
		}
		
		if (!StringUtils.hasLength(person.getSamaccountName())) {
			log.error("Will not update localUserId for person " + person.getId() + ". The person does not have a sAMAccountName");
			return "sAMAccountName is not null";
		}
		
		// step 0 - set the local userId
		String url = config.getNemLoginApi().getBaseUrl() + "/api/administration/identity/employee/" + person.getNemloginUserUuid() + "/requestcredentials";

		// sAMAccountName + @ + domain-alias
		String username = person.getSamaccountName() + person.getDomain().getNemLoginDomain();

		EmployeeChangeLocalUserIdRequest body = new EmployeeChangeLocalUserIdRequest();
		body.setSubjectNameId(username);

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json");
		headers.add("Authorization", "Bearer " + self.fetchToken());

		HttpEntity<EmployeeChangeLocalUserIdRequest> request2 = new HttpEntity<>(body, headers);

		try {
			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request2, String.class);

			if (response.getStatusCode().value() >= 200 && response.getStatusCode().value() <= 299) {
				;
			}
			else {
				log.error("Failed to update localUserId for person with uuid " + person.getUuid() + ". Error: " + response.getBody());
				return "Error: " + response.getBody();
			}
		}
		catch (Exception ex) {
			log.error("Failed to update localUserId for person with uuid " + person.getUuid(), ex);
			return "Exception: " + ex.getMessage();
		}

		if (person.isPrivateMitId()) {
			nemloginQueueService.save(new NemloginQueue(person, NemloginAction.ASSIGN_PRIVATE_MIT_ID));
		}

		return null;
	}

	private String revokePrivateMitId(Person person) {
		if (person == null) {
			log.error("Will not revoke PrivateMitId. Person was null");
			return "person is null";
		}

		// no NemLog-in, then just skip this order
		if (!StringUtils.hasLength(person.getNemloginUserUuid())) {
			return null;
		}
		
		log.info("Revoking PrivateMitId on person " + person.getId());

		if (!StringUtils.hasLength(person.getNemloginUserUuid())) {
			log.error("Will not revoke PrivateMitId for person " + person.getId() + ". The person does not have a nemloginUserUuid");
			return "nemloginUserUuid is null";
		}

		if (!StringUtils.hasLength(person.getSamaccountName())) {
			log.error("Will not revoke PrivateMitId for person " + person.getId() + ". The person does not have a sAMAccountName");
			return "sAMAccountName is not null";
		}

		try {
			// step 1 - fetch uuid of PrivateMitId "authenticator"
			String uuidOfPrivateMitId = getUuidOfPrivateMitId(person);
			
			// if no MitID present on user, just skip the removal :)
			if (!StringUtils.hasLength(uuidOfPrivateMitId)) {
				return null;
			}

			// step 2 - revoke PrivateMitId
			revokePrivateMitId(person, uuidOfPrivateMitId);
		}
		catch (Exception ex) {
			log.error("Failed to remove private MitID from " + person.getId() + " / " + person.getSamaccountName(), ex);
			return "Technical error: " + ex.getMessage();
		}

		return null;
	}

	private String getUuidOfPrivateMitId(Person person) {
		String url = config.getNemLoginApi().getBaseUrl() + "/api/administration/identity/employee/" + person.getNemloginUserUuid() + "/authenticators";

		HttpHeaders headers = new HttpHeaders();
		headers.add("Authorization", "Bearer " + self.fetchToken());

		HttpEntity<Object> fetchAuthenticatorsRequest = new HttpEntity<>(headers);

		ResponseEntity<AuthenticatorsResponse> response = restTemplate.exchange(url, HttpMethod.GET, fetchAuthenticatorsRequest, AuthenticatorsResponse.class);

		if (response.getStatusCode().is2xxSuccessful()) {
			AuthenticatorsResponse body = response.getBody();
			
			if (body != null && body.authenticators != null && !body.authenticators.isEmpty()) {
				Optional<Authenticator> first = body.authenticators.stream().filter(authenticator -> "PrivateMitId".equals(authenticator.getType())).findFirst();

				if (first.isPresent()) {
					return first.get().getUuid();
				}
			}
		}

		return null;
	}

	private void revokePrivateMitId(Person person, String mitIdUuid) throws Exception {
		String url = config.getNemLoginApi().getBaseUrl() + "/api/administration/identity/employee/" + person.getNemloginUserUuid() + "/revokecredential/" + mitIdUuid;

		HttpHeaders headers = new HttpHeaders();
		headers.add("Authorization", "Bearer " + self.fetchToken());

		HttpEntity<Object> fetchAuthenticatorsRequest = new HttpEntity<>(headers);

		ResponseEntity<AuthenticatorsResponse> response = restTemplate.exchange(url, HttpMethod.GET, fetchAuthenticatorsRequest, AuthenticatorsResponse.class);

		if (!response.getStatusCode().is2xxSuccessful()) {
			throw new Exception("Failed to remove MitID - statusCode " + response.getStatusCode().value());
		}
	}

	private String assignPrivateMitId(Person person) {
		if (person == null) {
			log.error("Will not assign PrivateMitId. Person was null");
			return "person is null";
		}

		if (!StringUtils.hasLength(person.getNemloginUserUuid())) {
			log.warn("Will not assign PrivateMitId for person " + person.getId() + ". The person does not have a nemloginUserUuid");
			return "nemloginUserUuid is null";
		}

		log.info("Assigning PrivateMitId on person " + person.getId());

		if (!StringUtils.hasLength(person.getSamaccountName())) {
			log.error("Will not assign PrivateMitId for person " + person.getId() + ". The person does not have a sAMAccountName");
			return "sAMAccountName is not null";
		}
		
		String existingUuid = getUuidOfPrivateMitId(person);
		if (StringUtils.hasLength(existingUuid)) {
			log.warn("Will not assign PrivateMitId for person " + person.getId() + ". The person already has a private MitID");
			return null;
		}

		String url = config.getNemLoginApi().getBaseUrl() + "/api/administration/identity/employee/" + person.getNemloginUserUuid() + "/requestcredentials";

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json");
		headers.add("Authorization", "Bearer " + self.fetchToken());

		CredentialsRequest body = new CredentialsRequest();
		ArrayList<String> types = new ArrayList<>();
		types.add("PrivateNemIdMitId");
		body.setAuthenticatorSettingTypes(types);

		HttpEntity<CredentialsRequest> requestForPrivateMitID = new HttpEntity<>(body, headers);

		try {
			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestForPrivateMitID, String.class);

			if (!response.getStatusCode().is2xxSuccessful()) {
				log.error("Failed to assign PrivateMitId to person with uuid " + person.getUuid() + ". Error: " + response.getBody());
				return "Technical error: " + response.getBody();
			}
		}
		catch (Exception ex) {
			log.error("Failed to assign private MitID to " + person.getId() + " / " + person.getSamaccountName(), ex);
			return "Technical error: " + ex.getMessage();
		}

		return null;
	}

	private String createEmployee(Person person) {
		if (person == null) {
			log.error("Will not create employee. Person was null");
			return "personen findes ikke i databasen";
		}
		
		log.info("Creating person " + person.getId());
		
		if (!validCpr(person.getCpr()) || cprService.isFictionalCpr(person.getCpr())) {
			log.warn("Personen har ikke et gyldigt cpr nummer: " + person.getId() + " / " + person.getSamaccountName());
			return "Ugyldigt cpr nummer!";
		}
		
		if (!oldEnough(person.getCpr())) {
			log.warn("Personen er ikke fyldt 13: " + person.getId() + " / " + person.getSamaccountName());
			return "Ikke fyldt 13!";			
		}

		if (StringUtils.hasLength(person.getNemloginUserUuid())) {
			log.error("Will not create employee for person " + person.getId() + ". The person has a nemloginUserUuid " + person.getNemloginUserUuid());
			return "Personen har allerede en konto i MitID Erhverv";
		}

		if (!StringUtils.hasLength(person.getSamaccountName())) {
			log.error("Will not create employee for person " + person.getId() + ". The person does not have a sAMAccountName");
			return "Personen har ikke et AD brugernavn";
		}

		// make sure we have name information correct from CPR
		if (!person.isCprNameUpdated()) {
			try {
				Future<CprLookupDTO> cprFuture = cprService.getByCpr(person.getCpr());
				CprLookupDTO dto = (cprFuture != null) ? cprFuture.get(5, TimeUnit.SECONDS) : null;

				if (dto == null || dto.isDoesNotExist()) {
					return "Personen kunne ikke findes i CPR registeret";
				}

				if (cprService.updateName(person, dto)) {
					personService.save(person);
				}
			}
			catch (Exception ex) {
				return "CPR opslag ikke muligt";
			}
		}

		String email = person.getEmail();
		if (!StringUtils.hasLength(email)) {
			email = config.getNemLoginApi().getDefaultEmail();
			if (!StringUtils.hasLength(email)) {
				log.error("Will not create employee for person " + person.getId() + ". The person has no email");
				return "Der mangler en e-mail adresse på personen";
			}
		}

		String url = config.getNemLoginApi().getBaseUrl() + "/api/administration/identity/employee";

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json");
		headers.add("Authorization", "Bearer " + self.fetchToken());

		EmployeeCreateRequest body = new EmployeeCreateRequest();
		IdentityProfile identityProfile = new IdentityProfile();
		
		String[] nameSplit = person.getName().split(" ");
		String surname = nameSplit[nameSplit.length - 1];
		String givenName = person.getName().replace(" " + surname, "");
		
		// sometimes we get bad data with spaces at the end... and MitID Erhverv does not like that
		email = email.trim();
		givenName = givenName.trim();
		surname = surname.trim();
		
		identityProfile.setGivenName(givenName);
		identityProfile.setSurname(surname);
		identityProfile.setEmailAddress(email);
		identityProfile.setCprNumber(person.getCpr());
		identityProfile.setPseudonym(person.isNameProtected());
		
		if (!config.getNemLoginApi().isDisableSendingRid()) {
			identityProfile.setRid(person.getRid());
		}
		
		IdentityOrganizationProfile identityOrganizationProfile = new IdentityOrganizationProfile();
		if (person.getEan() != null && StringUtils.hasLength(person.getEan())) {
			identityOrganizationProfile.setInvoiceMethodUuid(person.getEan());
		}
		else {
			identityOrganizationProfile.setInvoiceMethodUuid(config.getNemLoginApi().getInvoiceMethodUuid());
		}

		IdentityAuthenticators identityAuthenticators = new IdentityAuthenticators();

		if (person.isPrivateMitId()) {
			identityAuthenticators.getAuthenticatorTypes().add("PrivateNemIdMitId");
		}

		identityAuthenticators.setSubjectNameId(person.getSamaccountName() + person.getDomain().getNemLoginDomain());

		body.setIdentityOrganizationProfile(identityOrganizationProfile);
		body.setIdentityProfile(identityProfile);
		body.setIdentityAuthenticators(identityAuthenticators);
		
		String uuid = null;
		HttpEntity<EmployeeCreateRequest> request = new HttpEntity<>(body, headers);

		try {
			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
			if (response.getBody() != null) {
				if (response.getStatusCode().value() == 200) {
					ObjectMapper mapper = new ObjectMapper();
					EmployeeCreateResponse responseBody = mapper.readValue(response.getBody(), EmployeeCreateResponse.class);
					uuid = responseBody.getEmployeeUuid();

					if (StringUtils.hasLength(uuid)) {
						person.setNemloginUserUuid(uuid);
						personService.save(person);

						auditLogger.createdNemLoginUser(person);

						EmailTemplate emailTemplate = emailTemplateService.findByTemplateType(EmailTemplateType.MITID_ACTIVATED);
						for (EmailTemplateChild child : emailTemplate.getChildren()) {
							if (child.isEnabled() && child.getDomain().getId() == person.getDomain().getId()) {
								String message = EmailTemplateService.safeReplacePlaceholder(child.getMessage(), EmailTemplateService.RECIPIENT_PLACEHOLDER, person.getName());
								message = EmailTemplateService.safeReplacePlaceholder(message, EmailTemplateService.USERID_PLACEHOLDER, person.getSamaccountName());
								emailTemplateSenderService.send(person.getEmail(), person.getCpr(), person, child.getTitle(), message, child, true);
							}
						}

						return null;
					}
					else {
						return "Teknisk fejl: Ikke muligt at finde UUID på brugeren";
					}
				}
				else {
					if (response.getBody() != null && response.getBody().contains("See log for exception details")) {
						ObjectMapper mapper = new ObjectMapper();
						String payload = mapper.writeValueAsString(request);
						
						log.warn("Failed to create employee with nemloginApi. Person with uuid " + person.getUuid() + ". Msg: " + response.getBody() + ". Payload send is: " + payload);
					}
					else {
						log.error("Failed to create employee with nemloginApi. Person with uuid " + person.getUuid() + ". Msg: " + response.getBody());
					}
					
					return "Fejlbesked: " + response.getBody();
				}
			}
			else {
				log.error("Failed to create employee with nemloginApi (empty response body). Person with uuid " + person.getUuid());
				return "Fejlbesked: Intet svar fra MitID Erhverv";
			}
		}
		catch (Exception ex) {
			log.error("Failed to create employee with nemloginApi. Person with uuid " + person.getUuid(), ex);
			return "Teknisk fejl: " + ex.getMessage();
		}
	}
	
	private String changeEmail(Person person) {
		if (person == null) {
			log.error("Will not change email. Person was null");
			return "person is null";
		}
		
		log.info("Updating email on person " + person.getId());
		
		if (!StringUtils.hasLength(person.getNemloginUserUuid())) {
			log.error("Will not change email for person " + person.getId() + ". The person does not have a nemloginUserUuid");
			return "nemloginUserUuid is null";
		}
		
		String email = person.getEmail();
		if (!StringUtils.hasLength(email)) {
			email = config.getNemLoginApi().getDefaultEmail();
			if (!StringUtils.hasLength(email)) {
				log.error("Will not change email for person " + person.getId() + ". The person has no email");
				return "email is null";
			}
		}
		
		String url = config.getNemLoginApi().getBaseUrl() + "/api/administration/identity/employee/" + person.getNemloginUserUuid() + "/profile/nonsensitive";

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json");
		headers.add("Authorization", "Bearer " + self.fetchToken());

		EmployeeChangeEmailRequest body = new EmployeeChangeEmailRequest();
		body.setEmailAddress(email);

		HttpEntity<EmployeeChangeEmailRequest> request = new HttpEntity<>(body, headers);

		try {
			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, request, String.class);

			if (response.getStatusCode().value() == 200) {
				;
			}
			else {
				log.error("Failed to change email for person with uuid " + person.getUuid() + ". Error: " + response.getBody());
				return "statusCode = " + response.getStatusCode().value();
			}
		}
		catch (Exception ex) {
			log.error("Failed to change email for person with uuid " + person.getUuid(), ex);
			return "Exception: " + ex.getMessage();
		}

		return null;
	}

	private String deleteEmployee(String nemloginUserUuid) {
		if (nemloginUserUuid == null) {
			log.error("Failed to delete employee from nemlogin. The nemloginUserUuid was null.");
			return "nemloginUserUuid is null";
		}
		
		log.info("Deleting " + nemloginUserUuid);

		String url = config.getNemLoginApi().getBaseUrl() + "/api/administration/identity/employee/" + nemloginUserUuid;

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json");
		headers.add("Authorization", "Bearer " + self.fetchToken());

		EmployeeSearchRequest body = new EmployeeSearchRequest();

		HttpEntity<EmployeeSearchRequest> request = new HttpEntity<>(body, headers);

		try {
			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.DELETE, request, String.class);

			if (response.getStatusCode().value() == 204 || response.getStatusCode().value() == 404) {
				;
			}
			else {
				log.error("Failed to delete nemlogin employee for deleted person with nemloginUserUuid " + nemloginUserUuid + ". statusCode = " + response.getStatusCode().value());
				return "statusCode = " + response.getStatusCode().value();
			}
		}
		catch (Exception ex) {
			log.error("Failed to delete nemlogin employee for deleted person with nemloginUserUuid " + nemloginUserUuid, ex);
			return "Exception: " + ex.getMessage();
		}
		
		return null;
	}

	private String suspendEmployee(Person person) {
		if (person == null) {
			log.error("Failed to suspend employee from nemlogin. Person was null");
			return "person is null";
		}
		
		log.info("Suspending person " + person.getId());

		if (!StringUtils.hasLength(person.getNemloginUserUuid())) {
			log.error("Failed to suspend employee from nemlogin. The nemloginUserUuid was null on person with uuid " + person.getUuid());
			return "nemloginUserUuid is null";
		}
		
		String url = config.getNemLoginApi().getBaseUrl() + "/api/administration/identity/employee/" + person.getNemloginUserUuid() + "/suspend";

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json");
		headers.add("Authorization", "Bearer " + self.fetchToken());

		EmployeeSearchRequest body = new EmployeeSearchRequest();

		HttpEntity<EmployeeSearchRequest> request = new HttpEntity<>(body, headers);

		try {
			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, request, String.class);

			if (response.getStatusCode().value() == 204) {
				auditLogger.suspendedNemLoginUser(person);
				
				EmailTemplate emailTemplate = emailTemplateService.findByTemplateType(EmailTemplateType.MITID_DEACTIVATED);
				for (EmailTemplateChild child : emailTemplate.getChildren()) {
					if (child.isEnabled() && child.getDomain().getId() == person.getDomain().getId()) {
						String message = EmailTemplateService.safeReplacePlaceholder(child.getMessage(), EmailTemplateService.RECIPIENT_PLACEHOLDER, person.getName());
						message = EmailTemplateService.safeReplacePlaceholder(message, EmailTemplateService.USERID_PLACEHOLDER, person.getSamaccountName());
						emailTemplateSenderService.send(person.getEmail(), person.getCpr(), person, child.getTitle(), message, child, true);
					}
				}
			}
			else if (response.getStatusCode().value() == 404) {
				log.info("Could not suspend " + person.getSamaccountName() + " (" + person.getId() + "), so clearing NL UUID instead");

				// account in MitID Erhverv no longer exists - so just remove the stored UUID, suspension is not needed
				person.setNemloginUserUuid(null);
				personService.save(person);
			}
			else {
				log.warn("Failed to suspend nemlogin employee for person with nemloginUserUuid " + person.getNemloginUserUuid() + ". StatusCode=" + response.getStatusCode().value());
				return "statusCode = " + response.getStatusCode().value();
			}
		}
		catch (Exception ex) {
			log.error("Failed to suspend nemlogin employee for person with nemloginUserUuid " + person.getNemloginUserUuid(), ex);
			return "Exception: " + ex.getMessage();
		}
		
		return null;
	}
	
	private String reactivateEmployee(Person person) {
		if (person == null) {
			log.error("Failed to reactivate employee from nemlogin. Person was null");
			return "person is null";
		}
		
		log.info("Reactivating person " + person.getId());

		if (!StringUtils.hasLength(person.getNemloginUserUuid())) {
			log.error("Failed to reactivate employee from nemlogin. The nemloginUserUuid was null on person " + person.getId());
			return "nemloginUserUuid is null";
		}
		
		String url = config.getNemLoginApi().getBaseUrl() + "/api/administration/identity/employee/" + person.getNemloginUserUuid() + "/reactivate";

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json");
		headers.add("Authorization", "Bearer " + self.fetchToken());

		HttpEntity<String> request = new HttpEntity<>(headers);

		try {
			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, request, String.class);

			if (response.getStatusCode().value() == 204) {
				auditLogger.reactivatedNemLoginUser(person);
				
				EmailTemplate emailTemplate = emailTemplateService.findByTemplateType(EmailTemplateType.MITID_ACTIVATED);
				for (EmailTemplateChild child : emailTemplate.getChildren()) {
					if (child.isEnabled() && child.getDomain().getId() == person.getDomain().getId()) {
						String message = EmailTemplateService.safeReplacePlaceholder(child.getMessage(), EmailTemplateService.RECIPIENT_PLACEHOLDER, person.getName());
						message = EmailTemplateService.safeReplacePlaceholder(message, EmailTemplateService.USERID_PLACEHOLDER, person.getSamaccountName());
						emailTemplateSenderService.send(person.getEmail(), person.getCpr(), person, child.getTitle(), message, child, true);
					}
				}
			}
			else if (response.getStatusCode().value() == 404) {
				log.info("Could not reactivate " + person.getSamaccountName() + " (" + person.getId() + "), so clearing NL UUID, and doing a CREATE next night");
				
				// account in MitID Erhverv no longer exists - reactivating is not possible - we should instead attempt to create
				// an account, which will happen automatically during the nightly job because transferToNemLogin is true and NemloginUserUuid is not null ;)
				person.setNemloginUserUuid(null);
				personService.save(person);
			}
			else if (response.getStatusCode().value() == 409) {
				String body = response.getBody();
				
				// just try again - no worries (and no errors)
				if (body != null && body.contains("OptimisticConcurrency")) {
					log.warn("Failed to reactivate nemlogin employee for person with nemloginUserUuid " + person.getNemloginUserUuid() + ". StatusCode=" + response.getStatusCode().value() + " / body=" + response.getBody());
					return "statusCode = " + response.getStatusCode().value();
				}
				else {
					log.warn("Failed to reactivate nemlogin employee for person with nemloginUserUuid " + person.getNemloginUserUuid() + ". StatusCode=" + response.getStatusCode().value() + " / body=" + response.getBody());
					return "statusCode = " + response.getStatusCode().value();
				}
			}
			else {
				log.error("Failed to reactivate nemlogin employee for person with nemloginUserUuid " + person.getNemloginUserUuid() + ". StatusCode=" + response.getStatusCode().value() + " / body=" + response.getBody());
				return "statusCode = " + response.getStatusCode().value();
			}
		}
		catch (Exception ex) {
			log.error("Failed to reactivate nemlogin employee for person with nemloginUserUuid " + person.getNemloginUserUuid(), ex);
			return "Exception: " + ex.getMessage();
		}
		
		return null;
	}
	
	private List<Employee> getAllPendingMigratedEmployees() {
		String url = config.getNemLoginApi().getBaseUrl() + "/api/admin/organization-activation/migrated-identities/0";
		List<Employee> employees = null;

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json");
		headers.add("Authorization", "Bearer " + self.fetchToken());

		HttpEntity<String> request = new HttpEntity<>(headers);

		try {
			ResponseEntity<EmployeeSearchResponse> response = restTemplate.exchange(url, HttpMethod.GET, request, EmployeeSearchResponse.class);

			if (response.getStatusCode().value() == 200 && response.getBody() != null) {
				employees = response.getBody().getEmployees();
				log.info("Found " + employees.size() + " employees");
			}
			else {
				log.error("Failed to fetch all employees from nemloginApi. StatusCode=" + response.getStatusCode().value());
			}
		}
		catch (Exception ex) {
			log.error("Failed to fetch all employees from nemloginApi", ex);
		}
		
		return employees;
	}
	
	private FullEmployee getFullEmployee(String uuid) {
		FullEmployee employee = null;
		
		String url = config.getNemLoginApi().getBaseUrl() + "/api/administration/identity/employee/" + uuid;

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json");
		headers.add("Authorization", "Bearer " + self.fetchToken());

		HttpEntity<String> request = new HttpEntity<>(headers);

		ResponseEntity<FullEmployee> response = restTemplate.exchange(url, HttpMethod.GET, request, FullEmployee.class);

		if (response.getStatusCode().value() == 200 && response.getBody() != null) {
			employee = response.getBody();
		}

		return employee;
	}
	
	private List<Employee> getAllExistingEmployees(boolean readFullEmployee) {
		String url = config.getNemLoginApi().getBaseUrl() + "/api/administration/identity/employee/search";
		List<Employee> employees = null;

		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json");
		headers.add("Authorization", "Bearer " + self.fetchToken());

		EmployeeSearchRequest body = new EmployeeSearchRequest();

		HttpEntity<EmployeeSearchRequest> request = new HttpEntity<>(body, headers);

		// dear god - the error handling. Maybe create a decode utility method that handles this boilerplate :)

		try {
			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

			if (response.getStatusCode().value() == 200 && response.getBody() != null) {
				try {
					ObjectMapper mapper = new ObjectMapper();
					EmployeeSearchResponse responseBody = mapper.readValue(response.getBody(), EmployeeSearchResponse.class);
	
					employees = responseBody.getEmployees();
					log.info("Found " + employees.size() + " employees");
				}
				catch (JacksonException ex) {
					if (++readAllIdentitiesFailureInARow > 3) {
						log.error("Failed to fetch all employees from nemloginApi (" + readAllIdentitiesFailureInARow + " times). StatusCode=" + response.getStatusCode().value() + ", body=" + response.getBody(), ex);
					}
					else {
						log.warn("Failed to fetch all employees from nemloginApi. StatusCode=" + response.getStatusCode().value() + ", body=" + response.getBody(), ex);
					}
					
					return null;
				}
			}
			else {
				if (++readAllIdentitiesFailureInARow > 3) {
					log.error("Failed to fetch all employees from nemloginApi (" + readAllIdentitiesFailureInARow + " times). StatusCode=" + response.getStatusCode().value() + ", body=" + response.getBody());
				}
				else {
					log.warn("Failed to fetch all employees from nemloginApi. StatusCode=" + response.getStatusCode().value() + ", body=" + response.getBody());
				}
				
				return null;
			}
		}
		catch (ResourceAccessException ex) {
			// very unstable endpoint :)
			if (ex.getCause() instanceof HttpHostConnectException) {
				log.warn("Failed to fetch all employees from nemloginApi", ex);
			}
			else {
				if (++readAllIdentitiesFailureInARow > 3) {
					log.error("Failed to fetch all employees from nemloginApi (" + readAllIdentitiesFailureInARow + " times).", ex);
				}
				else {
					log.warn("Failed to fetch all employees from nemloginApi", ex);
				}
			}
			
			return null;
		}
		
		readAllIdentitiesFailureInARow = 0;

		if (employees != null && readFullEmployee) {
			log.info("Reading full employee data");

			try {
				for (Employee employee : employees) {
					FullEmployee fullEmployee = getFullEmployee(employee.getUuid());
					if (fullEmployee != null) {
						if (fullEmployee.getIdentityProfile() != null) {
							employee.setQualifiedSignature(fullEmployee.getIdentityProfile().isAllowQualifiedCertificateIssuance());
						}
					}
				}
			}
			catch (Exception ex) {
				// this happens quite often *sigh*
				if (ex.getMessage() != null && ex.getMessage().contains("ServiceUnavailable")) {
					log.warn("Failed to fetch details on employees", ex);					
				}
				else {
					log.error("Failed to fetch details on employees", ex);
				}

				return null;
			}
		}
		
		return employees;
	}
	
	private static long mitIDErhvervFullSyncErrorCounter = 0;
	
	@Transactional
	public void syncMitIDErhvervCache() {

		// start with a fresh token, just to ensure we don't expire during our run
		self.cleanUpToken();
		
		// find all existing employees in MitID Erhverv
		List<Employee> emps = getAllExistingEmployees(true);
		if (emps == null) {
			// 4 days with errors in a row, and we log an error
			if (++mitIDErhvervFullSyncErrorCounter >= 4) {
				log.error("Failed to perform sync of MitID Erhverv Cache at least 4 days in a row...");
			}
			
			return;
		}
		
		// reset
		mitIDErhvervFullSyncErrorCounter = 0;

		List<MitidErhvervCache> employees = emps.stream().map(e -> mitidErhvervCacheService.fromEmployee(e)).collect(Collectors.toList());
		Map<Long, MitidErhvervCache> employeesMap = employees.stream().collect(Collectors.toMap(MitidErhvervCache::getMitidErhvervId, Function.identity()));

		// load our cache
		List<MitidErhvervCache> mitidErhvervCaches = mitidErhvervCacheService.findAll();
		Map<Long, MitidErhvervCache> mitidErhvervCacheMap = mitidErhvervCaches.stream().collect(Collectors.toMap(MitidErhvervCache::getMitidErhvervId, Function.identity()));
		
		List<MitidErhvervCache> toSave = new ArrayList<>();
		
		// find new/update events
		for (MitidErhvervCache employee : employees) {
			MitidErhvervCache hit = mitidErhvervCacheMap.get(employee.getMitidErhvervId());
			
			if (hit == null) {
				// create scenario
				toSave.add(employee);
			}
			else {
				// update if needed
				if (!employee.equalsTo(hit)) {
					hit.copyFields(employee);
					toSave.add(hit);
				}
			}
		}

		// save if needed
		if (!toSave.isEmpty()) {
			log.info("Updating/saving " + toSave.size() + " employees in cache");
			mitidErhvervCacheService.saveAll(toSave);
		}
		
		// find to delete
		for (MitidErhvervCache cache : mitidErhvervCaches) {
			MitidErhvervCache hit = employeesMap.get(cache.getMitidErhvervId());
			
			if (hit == null) {
				log.info("Deleting " + cache.getUuid() + " from cache");
				mitidErhvervCacheService.delete(cache);
			}
		}
	}

	@Transactional
	public void checkForIncorrectDataEntries() {
		List<Person> personList = personService.getByTransferToNemLogin();
		List<MitidErhvervCache> cacheList = mitidErhvervCacheService.findAll();

		Map<String, MitidErhvervCache> mitIdCacheMap = cacheList
				.stream()
				.collect(Collectors.toMap(MitidErhvervCache::getUuid, Function.identity()));

		List<MitIdErhvervAccountError> existingErrors = mitIdErhvervAccountErrorService.getAll();
		List<MitIdErhvervAccountError> newErrors = new ArrayList<>();

		// check state in NL3 for each person with a NL3 UUID
		for (Person person : personList) {
			// need to have a NL3 UUId for these checks to make sense
			if (!StringUtils.hasLength(person.getNemloginUserUuid())) {
				continue;
			}
			
			// do not check against locked persons
			if (person.isLocked()) {
				continue;
			}

			MitidErhvervCache mitidErhvervCache = mitIdCacheMap.get(person.getNemloginUserUuid());
			if (mitidErhvervCache != null) {
				
				// no longer active, but person is active, so create a task to reactivate
				if (Objects.equals(mitidErhvervCache.getStatus(), "Suspended")) {
					MitIdErhvervAccountError error = new MitIdErhvervAccountError();
					error.setPerson(person);
					error.setErrorType(MitIdErhvervAccountErrorType.ACCOUNT_DISABLED_IN_MITID_ERHVERV);
					error.setNemloginUserUuid(person.getNemloginUserUuid());

					if (!existingErrors.contains(error)) {
						newErrors.add(error);
					}
					else {
						// matched - so we can remove it from the set so we do not delete it later
						existingErrors.remove(error);
					}
				}
			}
			else {

				// no longer exists, but person is active, so create a task to re-create
				MitIdErhvervAccountError error = new MitIdErhvervAccountError();
				error.setPerson(person);
				error.setErrorType(MitIdErhvervAccountErrorType.ACCOUNT_DELETED_IN_MITID_ERHVERV);
				error.setNemloginUserUuid(person.getNemloginUserUuid());

				if (!existingErrors.contains(error)) {
					newErrors.add(error);
				}
				else {
					// matched - so we can remove it from the set so we do not delete it later
					existingErrors.remove(error);
				}
			}
		}

		Map<String, Person> personMap = personList.stream()
				.filter(person -> StringUtils.hasLength(person.getNemloginUserUuid()))
				.collect(Collectors.toMap(Person::getNemloginUserUuid, Function.identity()));

		// find all entries that exists in MitID Erhverv, but is not associated with a local account
		for (MitidErhvervCache cache : cacheList) {
			if (personMap.containsKey(cache.getUuid())) {
				continue;
			}

			MitIdErhvervAccountError error = null;
			boolean singleMatch = true;
			
			for (Person person : personList) {
				if (Objects.equals(person.getEmail(), cache.getEmail()) &&
					Objects.equals(cache.getCpr(), person.getCpr()) &&
					person.getNemloginUserUuid() == null) {

					if (error == null) {
						error = new MitIdErhvervAccountError();
						error.setPerson(person);
						error.setNemloginUserUuid(cache.getUuid());
						error.setErrorType(MitIdErhvervAccountErrorType.UNASSOCIATED_ACCOUNT_IN_MITID_ERHVERV);
					}
					else {
						singleMatch = false;
					}
				}
			}

			// we only create this error if we have a unique match
			if (error != null && singleMatch) {
				if (!existingErrors.contains(error)) {
					newErrors.add(error);
				}
				else {
					// matched - so we can remove it from the set so we do not delete it later
					existingErrors.remove(error);
				}
			}
		}

		// save all new errors
		if (newErrors.size() > 0) {
			log.warn("Found " + newErrors.size() + " out-of-sync problems with MitID Erhverv");
			mitIdErhvervAccountErrorService.saveAll(newErrors);
		}

		// remove any old errors
		if (existingErrors.size() > 0) {
			log.info("Removed " + existingErrors.size() + " old out-of-sync problems that has been resolved since last run");
			
			for (MitIdErhvervAccountError existingError : existingErrors) {
				mitIdErhvervAccountErrorService.delete(existingError);
			}
		}
	}

	@Transactional
	public void fixMissingCreateSuspendOrders() {

		// find all existing employees in MitID Erhverv
		List<Employee> existingEmployees = getAllExistingEmployees(false);
		if (existingEmployees == null) {
			return;
		}

		Set<String> activeMitIDEmployees = existingEmployees.stream()
				.filter(e -> Objects.equals(e.getStatus(), "Active"))
				.map(e -> e.getUuid())
				.collect(Collectors.toSet());
		
		// find all persons
		List<Person> persons = personService.getAll();
		
		List<Person> personsWithNemloginUserUuidButShouldBeSuspended = persons.stream()
				.filter(p -> p.getNemloginUserUuid() != null && !p.isTransferToNemlogin())
				.collect(Collectors.toList());
		
		List<NemloginQueue> actions = new ArrayList<>();

		for (Person person : personsWithNemloginUserUuidButShouldBeSuspended) {
			if (activeMitIDEmployees.contains(person.getNemloginUserUuid())) {
				actions.add(new NemloginQueue(person, NemloginAction.SUSPEND));
				log.info("Creating hotfix suspend for " + person.getId());
			}
		}
		
		// and make sure any missing create orders are created
    	List<NemloginQueue> queue = nemloginQueueService.getAll();    	
		
    	for (Person person : persons) {
    		if (!person.isLocked() && person.isTransferToNemlogin() && !StringUtils.hasLength(person.getNemloginUserUuid())) {
				// wipe existing failed in queue, clean slate so to speak
    			nemloginQueueService.deleteFailedByPerson(person);
				
				// check if there is an existing create order (not failed, as those are deleted above)
				if (!queue.stream().anyMatch(q -> q.getAction().equals(NemloginAction.CREATE) && q.getPerson().getId() == person.getId())) {
					actions.add(new NemloginQueue(person, NemloginAction.CREATE));
					log.info("Creating hotfix create for " + person.getId());
				}
    		}
    	}
    	
		if (!actions.isEmpty()) {
			nemloginQueueService.saveAll(actions);
		}
	}
	
	private void migrateExistingNemloginUsers() {
		log.error("Attempting to migrate all existing NemLog-in users - this should only be done ONCE, so remember to turn of this feature now!");

		List<NemloginQueue> actions = new ArrayList<>();

		List<Person> personList = personService.getAll().stream().filter(p -> StringUtils.hasLength(p.getRid()) && StringUtils.hasLength(p.getSamaccountName())).collect(Collectors.toList());
		if (personList.size() == 0) {
			log.error("No persons with RID in database - aborting!");
			return;
		}

		// find all existing employees and update localLogin on them
		List<Employee> existingEmployees = getAllExistingEmployees(false);
		if (existingEmployees == null) {
			return;
		}

		for (Employee existingEmployee : existingEmployees) {
			Person match = personList.stream().filter(p -> p.getRid() != null && p.getRid().equals(existingEmployee.getRid())).findAny().orElse(null);
			if (match == null) {
				continue;
			}
			
			// should not run migration multiple times, but just in case, we have this check
			if (StringUtils.hasLength(match.getNemloginUserUuid())) {
				continue;
			}
			
			if (!match.isLocked()) {
				log.debug("Migrated " + match.getName() + " (" + match.getId() + ") to NemLog-in with UUID " + existingEmployee.getUuid());

				match.setNemloginUserUuid(existingEmployee.getUuid());
				personService.save(match);

				actions.add(new NemloginQueue(match, NemloginAction.ASSIGN_LOCAL_USER_ID));
			}
		}
		
		// do a partial save for now (just in case the list below is empty)
		if (!actions.isEmpty()) {
			nemloginQueueService.saveAll(actions);
			actions.clear();
		}
				
		List<Employee> employees = getAllPendingMigratedEmployees();
		if (employees == null || employees.size() == 0) {
			log.error("Failed to migrate existing nemlogin users. The list of employees was null or empty");
			return;
		}

		for (Employee employee : employees) {
			Person match = personList.stream().filter(p -> p.getRid() != null && p.getRid().equals(employee.getRid())).findAny().orElse(null);
			if (match == null) {
				log.warn("No match for a person (" + (employee.getProfile() != null ? (employee.getProfile().getGivenName() + " " + employee.getProfile().getSurname()) : "???")  + ") with RID = " + employee.getRid() + " and email = " + employee.getEmailAddress());
				continue;
			}
			
			// should not run migration multiple times, but just in case, we have this check
			if (StringUtils.hasLength(match.getNemloginUserUuid())) {
				log.warn("RID " + employee.getRid() + " already migrated to person " + match.getId());
				continue;
			}
			
			if (!match.isLocked()) {
				log.debug("Migrated " + match.getName() + " (" + match.getId() + ") to NemLog-in with UUID " + employee.getUuid());

				match.setNemloginUserUuid(employee.getUuid());
				personService.save(match);

				actions.add(new NemloginQueue(match, NemloginAction.UPDATE_PROFILE));
			}
		}
		
		if (!actions.isEmpty()) {
			nemloginQueueService.saveAll(actions);
		}
		
		log.info("Migration completed!");
	}
	
	private static int getAgeFromCpr(String cpr) {
		try {
			String year = cpr.substring(4, 6);
			String sub = cpr.substring(6, 7);

			// sub = 0,1,2,3 < 2000
			// sub = 4 og year = 00-36  er 2000+
			// sub = 4 og year = 37-99  er 1937-1999
			// sub = 9 og year = 00-36  er 2000+
			// sub = 9 og year = 37-99  er 1937-1999
			switch (sub) {
				case "0":
				case "1":
				case "2":
				case "3":
					year = "19" + year;
					break;
				case "5":
				case "6":
				case "7":
				case "8":
					year = "20" + year;
					break;
				case "4":
				case "9":
					long val = Long.parseLong(year);
					if (val <= 36) {
						year = "20" + year;
					}
					else {
						year = "19" + year;
					}
					break;
			}
			
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("ddMMyyyy");
			LocalDate birthDate = LocalDate.parse(cpr.substring(0, 4) + year, formatter);

			return Period.between(birthDate, LocalDate.now()).getYears();
		}
		catch (Exception ex) {
			log.error("Failed to parse cpr: " + cpr);

			// if we cannot parse the cpr, then it is likely not something we want send to MitID Erhverv :)
			return 1;
		}
	}
	
	private boolean oldEnough(String cpr) {
		if (cpr == null || cpr.length() != 10) {
			return false;
		}
		
		for (char c : cpr.toCharArray()) {
			if (!Character.isDigit(c)) {
				return false;
			}
		}

		if (getAgeFromCpr(cpr) < 13) {
			return false;
		}

		return true;
	}
	
	// copied from EboksService
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
