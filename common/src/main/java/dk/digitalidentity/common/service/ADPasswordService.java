package dk.digitalidentity.common.service;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.PasswordChangeQueue;
import dk.digitalidentity.common.dao.model.PasswordSetting;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.ReplicationStatus;
import dk.digitalidentity.common.service.model.ADPasswordRequest;
import dk.digitalidentity.common.service.model.ADPasswordResponse;
import dk.digitalidentity.common.service.model.ADPasswordResponse.ADPasswordStatus;
import dk.digitalidentity.common.service.model.UnlockADAccountRequest;
import lombok.extern.slf4j.Slf4j;

@EnableScheduling
@Slf4j
@Service
public class ADPasswordService {
	private RestTemplate restTemplate = new RestTemplate();
	private Map<String, Integer> flaggedWebsocketDomains = new HashMap<>();
	private Map<String, Integer> websocketMaxConnections = new HashMap<>();

	@Autowired
	private CommonConfiguration configuration;

	@Autowired
	private PasswordChangeQueueService passwordChangeQueueService;

	@Autowired
	private PasswordSettingService passwordSettingService;

	@Autowired
	private DomainService domainService;
	
	@Autowired
	private PersonService personService;
	
	// clear max values at 02:00 every night
	@Scheduled(cron = "0 2 * * * *")
	public void resetCount() {
		websocketMaxConnections = new HashMap<>();
	}

	public boolean monitorConnection(String domain) {
		try {
			int sessionCount = getWebsocketSessionCount(domain);

			if (sessionCount > 0) {
				if (log.isDebugEnabled()) {
					log.debug("Websockets monitoring success. " + sessionCount + " active sessions");
				}

				flaggedWebsocketDomains.remove(domain);

				return true;
			}

			// three consecutive calls should fail before we get an alarm
			Integer failureCount = flaggedWebsocketDomains.get(domain);
			if (failureCount == null) {
				failureCount = Integer.valueOf(1);
				flaggedWebsocketDomains.put(domain, failureCount);
			}
			else {
				failureCount = Integer.valueOf(failureCount + 1);
			}

			if (failureCount >= 3) {
				log.error("Websockets monitoring error (count = " + failureCount + "). Number of activeSessions was: " + sessionCount + " for domain '" + domain + "'");
			}
			else {
				log.warn("Websockets monitoring warning (count = " + failureCount + "). Number of activeSessions was: " + sessionCount + " for domain '" + domain + "'");
				return true;
			}
		}
		catch (Exception ex) {
			log.error("Websockets monitoring error for domain '" + domain + "'", ex);
		}

		return false;
	}
	
	public Pair<Integer, Integer> getWebsocketSessionCountPair(String domain) {
		Integer current = getWebsocketSessionCount(domain);
		Integer currentMax = websocketMaxConnections.get(domain);

		if (currentMax == null || current > currentMax) {
			websocketMaxConnections.put(domain, current);
			currentMax = current;
		}
		
		return Pair.of(current, currentMax);
	}

	private int getWebsocketSessionCount(String domain) {
		try {
			HttpHeaders headers = new HttpHeaders();
			headers.add("apiKey", configuration.getAd().getApiKey());

			ResponseEntity<Integer> response = restTemplate.exchange(getURL("api/sessions?domain=" + domain) , HttpMethod.GET, new HttpEntity<Object>(headers), Integer.class);

			if (response.getStatusCode().value() == 200) {
				Integer responseValue = response.getBody();
				
				if (responseValue != null) {
					return responseValue;
				}
				
				log.warn("responseValue is null");
			}
			else {
				log.warn("Failed to get sessions: " + response.getStatusCode().value());
			}
		}
		catch (Exception ex) {
			log.error("Websockets monitoring error for domain '" + domain + "'", ex);
		}
		
		return 0;
	}

	public ADPasswordResponse.ADPasswordStatus validatePassword(Person person, String password) {
		if (person.getDomain().isStandalone()) {
			return ADPasswordStatus.FAILURE;
		}

		String message = "";
		try {
			RestTemplate restTemplate = new RestTemplate();
			ResponseEntity<ADPasswordResponse> response = restTemplate.exchange(getURL("api/validatePassword"), HttpMethod.POST, getRequest(person, password), ADPasswordResponse.class);

			if (response.getStatusCode().value() == 200) {
				ADPasswordResponse result = response.getBody();

				if (result != null) {
					message = result.getMessage();

					if (StringUtils.hasLength(message)) {
						String prefix = "Websocket returned a non null message: ";
						
						// special handling of this, it is just a connection issue, the user should be able to try again later
						if (message.contains("[TEXT_PARTIAL_WRITING]")) {
							log.warn(prefix + message);

							return ADPasswordStatus.TECHNICAL_ERROR;
						}
						else if (message.contains("E_ACCESSDENIED")) {
							log.warn("Insufficient permissions: " + message);

							return ADPasswordStatus.INSUFFICIENT_PERMISSION;
						}
						else if (message.contains("No authenticated WebSocket connection available") || message.contains("Timeout waiting for response")) {
							log.warn(prefix + message);

							return ADPasswordStatus.TIMEOUT;
						}
						else {
							log.error(prefix + message);
						}
					}

					return result.getStatus();
				}
			}

			return ADPasswordStatus.FAILURE;
		}
		catch (RestClientException ex) {
			log.error("Failed to connect to AD Password validation service for " + person.getId(), ex);
			return ADPasswordStatus.TECHNICAL_ERROR;
		}
	}

	@Transactional(rollbackFor = Exception.class)
	public void syncPasswordsToAD() {

		// Load all settings once to minimize calls to the database
		List<PasswordSetting> allSettings = passwordSettingService.getAllSettings();
		Map<Domain, PasswordSetting> passwordSettingMap = allSettings.stream().collect(Collectors.toMap(PasswordSetting::getDomain, passwordSetting -> passwordSetting));

		for (PasswordChangeQueue change : passwordChangeQueueService.getUnsynchronized()) {
			Domain domain = domainService.getByName(change.getDomain());
			if (domain == null) {
				log.error("Unrecognized domain, skipping. changeId: " + change.getId());
				change.setMessage("Unknown domain");
				change.setStatus(ReplicationStatus.FINAL_ERROR);
				passwordChangeQueueService.save(change, false);
				continue;
			}

			// if for some reason a sync points to a subdomain, move it to the parent domain
			if (domain.getParent() != null) {
				log.warn("Got a password sync for child-domain for account " + change.getSamaccountName() + " moving sync to parent domain");
				domain = domain.getParent();
			}

			PasswordSetting passwordSetting = passwordSettingMap.get(domain);
			if (passwordSetting == null) {
				log.error("PasswordSettings for domain was null, skipping. domain: " + change.getDomain());
				change.setMessage("Missing configuration for domain");
				change.setStatus(ReplicationStatus.FINAL_ERROR);
				passwordChangeQueueService.save(change, false);
				continue;
			}

			if (domain.isStandalone()) {
				log.debug("Queued password change skipped since standAlone=true for associated domain");
				change.setMessage("Password replication disabled for domain");
				change.setStatus(ReplicationStatus.FINAL_ERROR);
				passwordChangeQueueService.save(change, false);
				continue;
			}

			// make sure we have a pointer to the right person
			final Domain fDomain = domain;
			List<Person> persons = personService.getBySamaccountName(change.getSamaccountName());
			persons = persons.stream().filter(p -> Objects.equals(p.getTopLevelDomain().getId(), fDomain.getId())).collect(Collectors.toList());

			if (persons.size() == 0) {
				log.error("Person did not exist in database for userId=" + change.getSamaccountName());
				change.setMessage("Missing person in database");
				change.setStatus(ReplicationStatus.FINAL_ERROR);
				passwordChangeQueueService.save(change, false);
				continue;
			}

			attemptPasswordReplication(persons.get(0), change);

			passwordChangeQueueService.save(change, ReplicationStatus.SYNCHRONIZED.equals(change.getStatus()));
		}
	}

	public ADPasswordResponse.ADPasswordStatus attemptPasswordReplication(Person person, PasswordChangeQueue change) {
		try {
			String url = (change.isChangeOnNextLogin()) ? "api/setPasswordWithForcedChange" : "api/setPassword";
			ResponseEntity<ADPasswordResponse> response = restTemplate.exchange(getURL(url), HttpMethod.POST, getRequest(change), ADPasswordResponse.class);

			ADPasswordResponse result = response.getBody();
			if (result == null) {
				log.error("No result on response");
				return ADPasswordStatus.TECHNICAL_ERROR;
			}

			if (response.getStatusCode().value() == 200 && ADPasswordStatus.OK.equals(result.getStatus())) {
				change.setStatus(ReplicationStatus.SYNCHRONIZED);
				change.setMessage(null);
			}
			else {
				// Setting status and message of change
				change.setStatus(ReplicationStatus.ERROR);
				String changeMessage = "Code: " + response.getStatusCode() + " Message: ";
				changeMessage += (result != null && result.getMessage() != null) ?  result.getMessage() : "NULL";
				change.setMessage(changeMessage);

				// Logging error/warn depending on how long it has gone unsynchronized
				if (change.getTts() != null && LocalDateTime.now().minusMinutes(10).isAfter(change.getTts())) {
					log.error("Replication failed, password change has not been replicated for more than 10 minutes (ID: " + change.getId() + ")");
					change.setStatus(ReplicationStatus.FINAL_ERROR);
				}
				else {
					log.warn("Password Replication failed, trying again in 1 minute (ID: " + change.getId() + ")");
				}
			}
			
			return result.getStatus();
		}
		catch (Exception ex) {
			change.setStatus(ReplicationStatus.ERROR);
			change.setMessage("Failed to connect to AD Password replication service: " + ex.getMessage());

			// tts null check to avoid issues with first attempt
			if (change.getTts() != null && LocalDateTime.now().minusMinutes(10).isAfter(change.getTts())) {
				log.error("Replication failed, password change has not been replicated for more than 10 minutes (ID: " + change.getId() + ")", ex);
				change.setStatus(ReplicationStatus.FINAL_ERROR);
			}
			else {
				log.warn("Password Replication failed, trying again in 1 minute (ID: " + change.getId() + ")");
			}
		}

		return ADPasswordStatus.TECHNICAL_ERROR;
	}
	
	public ADPasswordResponse.ADPasswordStatus attemptUnlockAccount(Person person) {
		try {
			ResponseEntity<ADPasswordResponse> response = restTemplate.exchange(getURL("api/unlockAccount") , HttpMethod.POST, getRequest(person), ADPasswordResponse.class);

			ADPasswordResponse result = response.getBody();
			if (result == null) {
				log.error("No result on response");
				return ADPasswordStatus.TECHNICAL_ERROR;
			}

			if (response.getStatusCode().value() != 200 || !ADPasswordStatus.OK.equals(result.getStatus())) {
				log.warn("Unlock account failed for person with uuid " + person.getUuid() + " and samaccountName " + person.getSamaccountName() + " with message: " + result.getMessage() + " (" +  result.getStatus() +")");
			}

			return result.getStatus();
		}
		catch (Exception ex) {
			log.warn("Unlock account failed for person with uuid " + person.getUuid() + " and samaccountName " + person.getSamaccountName(), ex);
		}

		return ADPasswordStatus.TECHNICAL_ERROR;
	}

	public void attemptRunPasswordExpiresSoonScript(Person person) {
		try {
			ResponseEntity<ADPasswordResponse> response = restTemplate.exchange(getURL("api/passwordExpires") , HttpMethod.POST, getRequest(person), ADPasswordResponse.class);

			ADPasswordResponse result = response.getBody();
			if (result == null) {
				log.warn("attemptRunPasswordExpiresSoonScript: No result on response");
				return;
			}

			if (response.getStatusCode().value() != 200 || !ADPasswordStatus.OK.equals(result.getStatus())) {
				log.warn("Run password expires soon script failed for person with uuid " + person.getUuid() + " and samaccountName " + person.getSamaccountName() + " with message: " + result.getMessage() + " (" +  result.getStatus() +")");
			}
		}
		catch (Exception ex) {
			log.warn("Run password expires soon script failed for person with uuid " + person.getUuid() + " and samaccountName " + person.getSamaccountName(), ex);
		}
	}

	@Transactional(rollbackFor = Exception.class)
	public void syncQueueCleanupTask() {
		
		// delete successful replications and purposely not replicated after 7 days
		List<PasswordChangeQueue> synchronizedChanges = passwordChangeQueueService.getByStatus(ReplicationStatus.SYNCHRONIZED);
		synchronizedChanges.addAll(passwordChangeQueueService.getByStatus(ReplicationStatus.DO_NOT_REPLICATE));

		for (PasswordChangeQueue synchronizedChange : synchronizedChanges) {
			LocalDateTime maxRetention = LocalDateTime.now().minusDays(7);

			if (synchronizedChange.getTts().isBefore(maxRetention)) {
				passwordChangeQueueService.delete(synchronizedChange);
			}
		}
		
		// then delete failures after 21 days
		synchronizedChanges = passwordChangeQueueService.getByStatus(ReplicationStatus.FINAL_ERROR);

		for (PasswordChangeQueue synchronizedChange : synchronizedChanges) {
			LocalDateTime maxRetention = LocalDateTime.now().minusDays(21);

			if (synchronizedChange.getTts().isBefore(maxRetention)) {
				passwordChangeQueueService.delete(synchronizedChange);
			}
		}
	}

	private String getURL(String endpoint) {
		String url = configuration.getAd().getBaseUrl();
		if (!url.endsWith("/")) {
			url += "/";
		}
		url += endpoint;

		return url;
	}

	private HttpEntity<ADPasswordRequest> getRequest(Person person, String password) {
		HttpHeaders headers = new HttpHeaders();
		headers.add("apiKey", configuration.getAd().getApiKey());

		ADPasswordRequest adPasswordRequest = new ADPasswordRequest(person, password);
		return new HttpEntity<>(adPasswordRequest, headers);
	}
	
	private HttpEntity<ADPasswordRequest> getRequest(PasswordChangeQueue change) throws IllegalBlockSizeException, InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidAlgorithmParameterException {
		HttpHeaders headers = new HttpHeaders();
		headers.add("apiKey", configuration.getAd().getApiKey());

		ADPasswordRequest adPasswordRequest = new ADPasswordRequest(change, passwordChangeQueueService.decryptPassword(change.getPassword()));
		return new HttpEntity<>(adPasswordRequest, headers);
	}
	
	private HttpEntity<UnlockADAccountRequest> getRequest(Person person) {
		HttpHeaders headers = new HttpHeaders();
		headers.add("apiKey", configuration.getAd().getApiKey());

		UnlockADAccountRequest unlockAccountRequest = new UnlockADAccountRequest(person);
		return new HttpEntity<>(unlockAccountRequest, headers);
	}
}
