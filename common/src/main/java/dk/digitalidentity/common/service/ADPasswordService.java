package dk.digitalidentity.common.service;

import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.springframework.beans.factory.annotation.Autowired;
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
import dk.digitalidentity.common.service.model.WebsocketConnectionStatus;
import dk.digitalidentity.common.service.model.WebsocketServerStatus;
import dk.digitalidentity.common.service.model.WebsocketSessionInfo;
import lombok.extern.slf4j.Slf4j;

@EnableScheduling
@Slf4j
@Service
public class ADPasswordService {
	private RestTemplate restTemplate = new RestTemplate();
	private Map<String, Integer> flaggedWebsocketDomains = new HashMap<>();
	private Map<String, WebsocketConnectionStatus> websocketStatusMap = new ConcurrentHashMap<>();

	@Autowired
	private CommonConfiguration configuration;

	@Autowired
	private EmailService emailService;

	@Autowired
	private PasswordChangeQueueService passwordChangeQueueService;

	@Autowired
	private PasswordSettingService passwordSettingService;

	@Autowired
	private DomainService domainService;

	public Map<String, WebsocketConnectionStatus> getWebsocketConnectionMap() {
		return websocketStatusMap;
	}
	
	// clear max connection counts at 02:00 every night (keep status and lastHealthy)
	@Scheduled(cron = "0 0 2 * * *")
	public void resetCount() {
		for (WebsocketConnectionStatus status : websocketStatusMap.values()) {
			status.setMaxConnections(0);
		}
	}

	public boolean monitorConnection(String domain) {
		try {
			Integer sessionCount = getWebsocketSessionCount(domain);

			if (sessionCount != null && sessionCount > 0) {
				if (log.isDebugEnabled()) {
					log.debug("Websockets monitoring success. " + sessionCount + " active sessions");
				}

				flaggedWebsocketDomains.remove(domain);

				return true;
			}

			int count = sessionCount != null ? sessionCount : 0;

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
				log.error("Websockets monitoring error (count = " + failureCount + "). Number of activeSessions was: " + count + " for domain '" + domain + "'");
			}
			else {
				log.warn("Websockets monitoring warning (count = " + failureCount + "). Number of activeSessions was: " + count + " for domain '" + domain + "'");
				return true;
			}
		}
		catch (Exception ex) {
			log.error("Websockets monitoring error for domain '" + domain + "'", ex);
		}

		return false;
	}

	// Fetches current session details from the websocket service and updates the in-memory status map for the given domain.
	// Tracks per-server up/down state, fires email alarms when a server has been down beyond the configured threshold,
	// and removes servers that have been down for more than 24 hours.
	public void updateWebsocketConnectionStatus(String domain) {
		WebsocketConnectionStatus status = websocketStatusMap.computeIfAbsent(domain, _ -> new WebsocketConnectionStatus());

		List<WebsocketSessionInfo> currentSessions = getWebsocketSessionDetails(domain);
		Set<String> activeKeys = currentSessions.stream()
			.map(s -> s.getServerName() != null ? s.getServerName() : String.valueOf(s.getId()))
			.collect(Collectors.toSet());

		Map<String, WebsocketServerStatus> serverMap = status.getServerMap();

		// Update servers that are currently active
		for (WebsocketSessionInfo session : currentSessions) {
			// Set key to server name or session id
			String key = session.getServerName() != null ? session.getServerName() : String.valueOf(session.getId());
			WebsocketServerStatus serverStatus = serverMap.computeIfAbsent(key, _ -> new WebsocketServerStatus());
			serverStatus.setServerName(session.getServerName());

			if (!serverStatus.isUp()) {
				// Server came back up — reset alarm so it fires again if it goes down later
				serverStatus.setAlarmSent(false);
				serverStatus.setDownSince(null);
			}

			serverStatus.setUp(true);
			serverStatus.setLastHealthy(LocalDateTime.now());
		}

		// Handle servers no longer in the active list
		int alarmThreshold = configuration.getAd().getAlarmThresholdMinutes();
		Iterator<Map.Entry<String, WebsocketServerStatus>> iterator = serverMap.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<String, WebsocketServerStatus> entry = iterator.next();
			WebsocketServerStatus serverStatus = entry.getValue();

			if (!activeKeys.contains(entry.getKey())) {
				if (serverStatus.getServerName() == null) {
					// Unnamed server disconnected — remove to avoid alarms
					iterator.remove();
					continue;
				}

				if (serverStatus.isUp()) {
					serverStatus.setUp(false);
					serverStatus.setDownSince(LocalDateTime.now());
				}

				// Fire alarm once when down longer than threshold
				if (!serverStatus.isAlarmSent() && serverStatus.getDownSince() != null && serverStatus.getDownSince().isBefore(LocalDateTime.now().minusMinutes(alarmThreshold))) {
					log.warn("Websocket server '{}' for domain '{}' has been down for more than {} minutes", serverStatus.getServerName(), domain, alarmThreshold);
					serverStatus.setAlarmSent(true);

					String emails = null;
					Domain actualDomain = domainService.getByName(domain);
					if (actualDomain != null) {
						PasswordSetting settings = passwordSettingService.getSettings(actualDomain);
						if (settings != null && settings.isMonitoringEnabled()) {
							emails = settings.getMonitoringEmail();
						}
					}

					if (StringUtils.hasLength(emails)) {
						String subject = "(OS2faktor " + configuration.getEmail().getFromName() + ") forbindelse til AD nede : " + serverStatus.getServerName();
						String message = "Forbindelsen til servicen 'OS2faktor Password Agent' der kører på serveren '" + serverStatus.getServerName() + "' for domænet '" + domain + "' har været utilgængelig i mere end " + alarmThreshold + " minutter.";
						emailService.sendMessage(emails, subject, message, null);
					}
				}
			}
		}

		// Remove servers that have been down for more than 24 hours
		serverMap.values().removeIf(s -> !s.isUp() && s.getDownSince() != null && s.getDownSince().isBefore(LocalDateTime.now().minusHours(24)));

		// Build the sessions list from tracking map
		List<WebsocketServerStatus> serverList = new ArrayList<>(serverMap.values());
		status.setSessions(serverList);

		// Update domain-level counters
		int current = (int) serverList.stream().filter(WebsocketServerStatus::isUp).count();
		status.setCurrentConnections(current);

		if (current > status.getMaxConnections()) {
			status.setMaxConnections(current);
		}
	}

	// Fetch info about AD sessions from websockets project
	private List<WebsocketSessionInfo> getWebsocketSessionDetails(String domain) {
		try {
			HttpHeaders headers = new HttpHeaders();
			headers.add("apiKey", configuration.getAd().getApiKey());

			ResponseEntity<WebsocketSessionInfo[]> response = restTemplate.exchange(
				getURL("api/sessionDetails?domain=" + domain),
				HttpMethod.GET,
				new HttpEntity<Object>(headers),
				WebsocketSessionInfo[].class
			);

			if (response.getStatusCode().value() == 200 && response.getBody() != null) {
				return Arrays.asList(response.getBody());
			}
		}
		catch (Exception ex) {
			log.error("Failed to get session details for domain '" + domain + "'", ex);
		}

		return Collections.emptyList();
	}

	private Integer getWebsocketSessionCount(String domain) {
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

		return null;
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

	// only to be used from the UI
	public ADPasswordStatus attemptPasswordChangeFromUI(Person person, String newPassword, boolean forceChangePassword) throws InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException, UnsupportedEncodingException, BadPaddingException, IllegalBlockSizeException, InvalidAlgorithmParameterException {
		PasswordChangeQueue change = new PasswordChangeQueue(person, passwordChangeQueueService.encryptPassword(newPassword), forceChangePassword);

		ADPasswordStatus status = attemptPasswordReplication(change);
		switch (status) {
			// inform user through UI (but also save result in queue for debugging purposes)
			case FAILURE:
			case TECHNICAL_ERROR:
			case INSUFFICIENT_PERMISSION:
				// FINAL_ERROR prevent any retries on this
				change.setStatus(ReplicationStatus.FINAL_ERROR);
				passwordChangeQueueService.save(change);
				break;

			case NOOP:
				log.error("Got a NOOP case here - that should not happen");
				break;

			// save result - so it is correctly logged to the queue
			case OK:
				passwordChangeQueueService.save(change);
				break;

			// delay replication in case of a timeout
			case TIMEOUT:
				passwordChangeQueueService.save(change);
				break;
		}

		return status;
	}

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

			attemptPasswordReplication(change);

			passwordChangeQueueService.save(change, ReplicationStatus.SYNCHRONIZED.equals(change.getStatus()));
		}
	}

	public ADPasswordResponse.ADPasswordStatus attemptPasswordReplication(PasswordChangeQueue change) {
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
				if (response.getBody() != null && response.getBody().getMessage() != null && response.getBody().getMessage().contains("PasswordException")) {
					// no reason to try again, this is a bad password
					change.setStatus(ReplicationStatus.FINAL_ERROR);
				}
				else {
					change.setStatus(ReplicationStatus.ERROR);

					// Logging error/warn depending on how long it has gone unsynchronized
					if (change.getTts() != null && LocalDateTime.now().minusMinutes(10).isAfter(change.getTts())) {
						log.error("Replication failed, password change has not been replicated for more than 10 minutes (ID: " + change.getId() + ")");
						change.setStatus(ReplicationStatus.FINAL_ERROR);
					}
					else {
						log.warn("Password Replication failed, trying again in 1 minute (ID: " + change.getId() + ")");
					}
				}

				String changeMessage = "Code: " + response.getStatusCode() + " Message: ";
				changeMessage += (result != null && result.getMessage() != null) ?  result.getMessage() : "NULL";
				change.setMessage(changeMessage);
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

	@Transactional(rollbackFor = Exception.class) // this is OK, isolated deleting
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
