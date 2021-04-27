package dk.digitalidentity.common.service;

import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.PasswordChangeQueue;
import dk.digitalidentity.common.dao.model.PasswordSetting;
import dk.digitalidentity.common.dao.model.enums.ReplicationStatus;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.service.model.ADPasswordRequest;
import dk.digitalidentity.common.service.model.ADPasswordResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ADPasswordService {

	// TODO: autowire RestTemplate

	@Autowired
	private CommonConfiguration configuration;

	@Autowired
	private PasswordChangeQueueService passwordChangeQueueService;

	@Autowired
	private PasswordSettingService passwordSettingService;

	@Autowired
	private DomainService domainService;

	public boolean validatePassword(Person person, String password) {
		if (!passwordSettingService.getSettings(person.getDomain()).isValidateAgainstAdEnabled()) {
			return false;
		}

		String message = "";
		try {
			RestTemplate restTemplate = new RestTemplate();
			ResponseEntity<ADPasswordResponse> response = restTemplate.exchange(getURL("api/validatePassword") , HttpMethod.POST, getRequest(person, password), ADPasswordResponse.class);

			if (response.getStatusCodeValue() == 200) {
				ADPasswordResponse result = response.getBody();

				if (result != null) {
					message = result.getMessage();
					if (!StringUtils.isEmpty(message)) {
						log.error("Websocket returned a non null message: " + message);
					}

					return result.isValid();
				}
			}

			return false;
		}
		catch (RestClientException ex) {
			log.warn("Failed to connect to AD Password validation service", ex);
		}

		log.warn("Could not validate password against AD for " + person.getSamaccountName() + ", Message: " + message);
		return false;
	}

	@Transactional(rollbackFor = Exception.class)
	public void syncPasswordsToAD() {
		RestTemplate restTemplate = new RestTemplate();

		// Load all settings once to minimize calls to the database
		List<PasswordSetting> allSettings = passwordSettingService.getAllSettings();
		Map<Domain, PasswordSetting> passwordSettingMap = allSettings.stream().collect(Collectors.toMap(PasswordSetting::getDomain, passwordSetting -> passwordSetting));

		for (PasswordChangeQueue change : passwordChangeQueueService.getUnsynchronized()) {
			Domain domain = domainService.getByName(change.getDomain());
			if (domain == null) {
				log.error("Unrecognized domain, skipping. changeId: " + change.getId());
				change.setMessage("Unknown domain");
				change.setStatus(ReplicationStatus.ERROR);
				passwordChangeQueueService.save(change);
				continue;
			}

			PasswordSetting passwordSetting = passwordSettingMap.get(domain);
			if (passwordSetting == null) {
				log.error("PasswordSettings for domain was null, skipping. domain: " + change.getDomain());
				change.setMessage("Missing configuration for domain");
				change.setStatus(ReplicationStatus.ERROR);
				passwordChangeQueueService.save(change);
				continue;
			}

			if (!passwordSetting.isReplicateToAdEnabled()) {
				log.debug("Queued password change skipped since isReplicateToAdEnabled=false for associated domain");
				change.setMessage("Password replication disabled for domain");
				change.setStatus(ReplicationStatus.ERROR);
				continue;
			}

			try {
				ResponseEntity<ADPasswordResponse> response = restTemplate.exchange(getURL("api/setPassword") , HttpMethod.POST, getRequest(change), ADPasswordResponse.class);

				ADPasswordResponse result = response.getBody();
				if (response.getStatusCodeValue() == 200 && result != null && result.isValid()) {
					change.setStatus(ReplicationStatus.SYNCHRONIZED);
				}
				else {
					// Setting status and message of change
					change.setStatus(ReplicationStatus.ERROR);
					String changeMessage = "Code: " + response.getStatusCode() + " Message: ";
					changeMessage += (result != null && result.getMessage() != null) ?  result.getMessage() : "NULL";
					change.setMessage(changeMessage);

					// Logging error/warn depending on how long it has gone unsynchronized
					if (LocalDateTime.now().minusMinutes(10).isAfter(change.getTts())) {
						// TODO: in case of failure we need to alert both us and the customer (if they have configured an alert URL)
						log.error("Replication failed, password change has not been replicated for more than 10 minutes (ID: " + change.getId() + ")");
					}
					else {
						log.warn("Password Replication failed, trying again in 1 minute (ID: " + change.getId() + ")");
					}
				}

				passwordChangeQueueService.save(change);
			}
			catch (Exception ex) {
				change.setStatus(ReplicationStatus.ERROR);
				change.setMessage("Failed to connect to AD Password replication service: " + ex.getMessage());
				passwordChangeQueueService.save(change);

				if (LocalDateTime.now().minusMinutes(10).isAfter(change.getTts())) {
					// TODO: in case of failure we need to alert both us and the customer (if they have configured an alert URL)
					log.error("Replication failed, password change has not been replicated for more than 10 minutes (ID: " + change.getId() + ")", ex);
				} else {
					log.warn("Password Replication failed, trying again in 1 minute (ID: " + change.getId() + ")");
				}
			}
		}
	}

	@Transactional(rollbackFor = Exception.class)
	public void syncQueueCleanupTask() {
		List<PasswordChangeQueue> synchronizedChanges = passwordChangeQueueService.getByStatus(ReplicationStatus.SYNCHRONIZED);

		for (PasswordChangeQueue synchronizedChange : synchronizedChanges) {
			LocalDateTime maxRetention = LocalDateTime.now().minusDays(7);

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

	private HttpEntity<ADPasswordRequest> getRequest(PasswordChangeQueue change) throws IllegalBlockSizeException, InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, NoSuchPaddingException {
		HttpHeaders headers = new HttpHeaders();
		headers.add("apiKey", configuration.getAd().getApiKey());

		ADPasswordRequest adPasswordRequest = new ADPasswordRequest(change, passwordChangeQueueService.decryptPassword(change.getPassword()));
		return new HttpEntity<>(adPasswordRequest, headers);
	}

	public boolean monitorConnection(String domain) {
		RestTemplate restTemplate = new RestTemplate();
		Integer result = 0;

		try {
			HttpHeaders headers = new HttpHeaders();
			headers.add("apiKey", configuration.getAd().getApiKey());

			ResponseEntity<Integer> response = restTemplate.exchange(getURL("api/sessions?domain=" + domain) , HttpMethod.GET, new HttpEntity<Object>(headers), Integer.class);

			result = response.getBody();
			if (response.getStatusCodeValue() == 200 && result != null && result > 0) {
				if (log.isDebugEnabled()) {
					log.debug("Websockets monitoring success. " + result + " active sessions");
				}

				return true;
			}

			log.error("Websockets monitoring error. Number of activeSessions was: " + result + " for domain '" + domain + "'. Status code was: " + response.getStatusCodeValue());
		}
		catch (Exception ex) {
			log.error("Websockets monitoring error for domain '" + domain + "'", ex);
		}

		return false;
	}
}
