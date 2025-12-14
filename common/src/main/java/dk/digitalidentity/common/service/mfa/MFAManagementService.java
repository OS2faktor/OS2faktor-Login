package dk.digitalidentity.common.service.mfa;

import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.CachedMfaClient;
import dk.digitalidentity.common.dao.model.MfaLoginHistory;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.mfa.model.AuthenticateUserRequestBody;
import dk.digitalidentity.common.service.mfa.model.ClientType;
import dk.digitalidentity.common.service.mfa.model.HardwareTokenDTO;
import dk.digitalidentity.common.service.mfa.model.LoginMfaResponse;
import dk.digitalidentity.common.service.mfa.model.MfaRenameRequestDTO;
import dk.digitalidentity.common.service.mfa.model.ProjectionClient;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class MFAManagementService {
	private static final String connectorVersion = "1.0.0";

	@Autowired
	private CommonConfiguration configuration;

	@Autowired
	private PersonService personService;
	
	@Autowired
	private AuditLogger auditLogger;
		
	@Autowired
	@Qualifier("defaultRestTemplate")
	private RestTemplate restTemplate;

	private String getURL(String endpoint) {
		String url = configuration.getMfa().getBaseUrl();
		if (!url.endsWith("/")) {
			url += "/";
		}
		url += endpoint;

		return url;
	}

	public List<ProjectionClient> lookupMfaClientsInCentral(Map<String, List<Person>> personMap) {
		HttpHeaders headers = new HttpHeaders();
		headers.add("ApiKey", configuration.getMfa().getManagementApiKey());
		headers.add("connectorVersion", connectorVersion);
		
		HttpEntity<String> entity = new HttpEntity<>("", headers);

		List<ProjectionClient> clients = new ArrayList<>();
		String lastClientDeviceId = null;

		boolean done = false;
		int counter = 0;

		do {
			ResponseEntity<LoginMfaResponse> response = restTemplate.exchange(
				getURL("/api/login/mfa/nonLocal" + ((lastClientDeviceId != null) ? ("?deviceId=" + lastClientDeviceId) : "")),
				HttpMethod.GET,
				entity,
				LoginMfaResponse.class);

			if (response.getStatusCode().value() == 200) {
				LoginMfaResponse body = response.getBody();
				if (body == null || body.getCount() == 0) {
					done = true;
				}
				else {
					lastClientDeviceId = body.getLastClientDeviceId();
					counter++;

					for (ProjectionClient client : body.getClients()) {
						if (personMap.containsKey(client.getSsn())) {
							clients.add(client);
						}
					}
				}
			}
			else {
				throw new RuntimeException("Failed to get MFA clients - HTTP " + response.getStatusCode().value());
			}
		}
		while (!done && counter < 100);
		
		if (counter == 100) {
			log.error("Did 100 calls, and we did not get everything yet");
		}

		if (clients.size() == 0) {
			throw new RuntimeException("Got 0 clients - that is a problem - abort!");
		}

		return clients;
	}

	public List<ProjectionClient> lookupLocalMfaClientsInCentral(Map<String, List<Person>> personMap) {
		HttpHeaders headers = new HttpHeaders();
		headers.add("ApiKey", configuration.getMfa().getManagementApiKey());
		headers.add("connectorVersion", connectorVersion);
		
		HttpEntity<String> entity = new HttpEntity<>("", headers);

		List<ProjectionClient> clients = new ArrayList<>();

		ResponseEntity<LoginMfaResponse> response = restTemplate.exchange(
			getURL("/api/login/mfa/local"),
			HttpMethod.GET,
			entity,
			LoginMfaResponse.class);

		if (response.getStatusCode().value() == 200) {
			LoginMfaResponse body = response.getBody();
			if (body != null && body.getCount() > 0) {
				for (ProjectionClient client : body.getClients()) {
					if (personMap.containsKey(client.getSsn())) {
						clients.add(client);
					}
				}
			}
		}
		else {
			throw new RuntimeException("Failed to get Local MFA clients - HTTP " + response.getStatusCode().value());
		}

		return clients;
	}
	
	public boolean renameMfaClient(String deviceId, String name) {
		HttpHeaders headers = new HttpHeaders();
		headers.add("ApiKey", configuration.getMfa().getManagementApiKey());
		headers.add("connectorVersion", connectorVersion);
		
		MfaRenameRequestDTO request = new MfaRenameRequestDTO();
		request.setDeviceId(deviceId);
		request.setName(name);
		HttpEntity<MfaRenameRequestDTO> entity = new HttpEntity<>(request, headers);

		try {
			ResponseEntity<Object> response = restTemplate.exchange(getURL("/api/login/renameClient"), HttpMethod.POST, entity, Object.class);
			if (response.getStatusCode().value() == 200) {
				return true;
			}
			
			log.error("Failed to rename client to '" + name + "' for deviceId = " + deviceId + " with statusCode = " + response.getStatusCode().value());
		}
		catch (Exception ex) {
			log.error("Failed to call MFA Login endpoint /api/login/renameClient", ex);
		}

		return false;
	}

	public boolean deleteMfaClient(String deviceId) {
		HttpHeaders headers = new HttpHeaders();
		headers.add("ApiKey", configuration.getMfa().getManagementApiKey());
		headers.add("connectorVersion", connectorVersion);
		HttpEntity<String> entity = new HttpEntity<>("", headers);

		try {
			ResponseEntity<Object> response = restTemplate.exchange(getURL("/api/login/disableClient/" + deviceId), HttpMethod.POST, entity, Object.class);
			if (response.getStatusCode() == HttpStatus.OK) {
				return true;
			}
		}
		catch (Exception ex) {
			log.error("Failed to call MFA Login endpoint /api/login/disableClient", ex);
		}

		return false;
	}
	
	public boolean setPrimaryMfaClient(String deviceId, boolean setPrimary) {
		HttpHeaders headers = new HttpHeaders();
		headers.add("ApiKey", configuration.getMfa().getManagementApiKey());
		headers.add("connectorVersion", connectorVersion);
		HttpEntity<String> entity = new HttpEntity<>("", headers);

		try {
			ResponseEntity<Object> response = restTemplate.exchange(getURL("/api/login/primaryClient/" + deviceId + "/" + setPrimary), HttpMethod.POST, entity, Object.class);
			if (response.getStatusCode() == HttpStatus.OK) {
				return true;
			}
		}
		catch (Exception ex) {
			log.error("Failed to call MFA Login endpoint /api/login/primaryClient", ex);
		}

		return false;
	}

	public record RobotMfaRegistrationRequest (String name) {};
	public record RobotMfaRegistrationResponse (String secret, String deviceId) {};

	public RobotMfaRegistrationResponse robotRegistionRequester(RobotMfaRegistrationRequest request) {
		HttpHeaders headers = new HttpHeaders();
		headers.add("ApiKey", configuration.getMfa().getManagementApiKey());
		headers.add("connectorVersion", connectorVersion);
		headers.setContentType(MediaType.APPLICATION_JSON);
		
		HttpEntity<RobotMfaRegistrationRequest> entity = new HttpEntity<>(request, headers);
		
		try {
			String url = configuration.getMfa().getBaseUrl() + "/api/login/robo/register";
			
			ResponseEntity<RobotMfaRegistrationResponse> response = restTemplate.exchange(url, HttpMethod.POST, entity, new ParameterizedTypeReference<RobotMfaRegistrationResponse>() {});
			
			return response.getBody();
		}
		catch (HttpClientErrorException ex) {
			log.error("Failed to call MFA robot register endpoint", ex);
		}
		
		return null;
	}
	
	public String authenticateUser(String cpr, NSISLevel nsisLevel, String type) {
		HttpHeaders headers = new HttpHeaders();
		headers.add("ApiKey", configuration.getMfa().getManagementApiKey());
		headers.add("connectorVersion", connectorVersion);

		AuthenticateUserRequestBody body = new AuthenticateUserRequestBody();
		body.setCpr(cpr);
		body.setNsisLevel(nsisLevel);

		HttpEntity<AuthenticateUserRequestBody> entity = new HttpEntity<>(body, headers);

		try {
			ResponseEntity<String> response = restTemplate.exchange(getURL("/api/login/authenticateUser?type=" + type), HttpMethod.POST, entity, String.class);
			if (response.getStatusCode() == HttpStatus.OK) {
				return response.getBody();
			}
		}
		catch (Exception ex) {
			log.error("Failed to call MFA Login endpoint /api/login/authenticateUser", ex);
		}

		return null;
	}

	public HardwareTokenDTO getHardwareToken(String serial) {
		HttpHeaders headers = new HttpHeaders();
		headers.add("ApiKey", configuration.getMfa().getManagementApiKey());
		headers.add("connectorVersion", connectorVersion);
		HttpEntity<String> entity = new HttpEntity<>(headers);

		try {
			String url = configuration.getMfa().getBaseUrl() + "/api/login/kodeviser/device?serial=" + serial;

			ResponseEntity<HardwareTokenDTO> response = restTemplate.exchange(url, HttpMethod.GET, entity, HardwareTokenDTO.class);

			return response.getBody();
		}
		catch (Exception ex) {
			log.error("Failed to get hardware token", ex);
		}

		return null;
	}

	public boolean deregisterHardwareToken(String serial) {
		HttpHeaders headers = new HttpHeaders();
		headers.add("ApiKey", configuration.getMfa().getManagementApiKey());
		headers.add("connectorVersion", connectorVersion);
		HttpEntity<String> entity = new HttpEntity<>(headers);

		try {
			String url = configuration.getMfa().getBaseUrl() + "/api/login/kodeviser/deregister?serial=" + serial;

			ResponseEntity<?> response = restTemplate.exchange(url, HttpMethod.GET, entity, Object.class);

			return (response.getStatusCode() == HttpStatus.OK);
		}
		catch (Exception ex) {
			log.error("Failed to deregister hardware token", ex);
		}

		return false;
	}
	
	public void removeTOTPHDevicesOnLockedPersons() {
		List<Person> lockedPersons = personService.findByLockedDatasetTrue(p -> {
			p.getMfaClients().size();
		});

		log.info("Found locked persons: " + lockedPersons.size());
		
		// filter out timestamp null
		lockedPersons = lockedPersons.stream().filter(p -> p.getLockedDatasetTts() != null).collect(Collectors.toList());

		log.info("Found locked persons with timestamp: " + lockedPersons.size());

		// filter persons that were locked more than 1 day ago but not more than 3 days ago
		LocalDateTime dayAgo = LocalDateTime.now().minusDays(1);
		LocalDateTime threeDaysAgo = LocalDateTime.now().minusDays(3);
		lockedPersons = lockedPersons.stream()
				.filter(p -> p.getLockedDatasetTts().isBefore(dayAgo) && p.getLockedDatasetTts().isAfter(threeDaysAgo))
				.collect(Collectors.toList());

		log.info("Found locked persons within 1-3 days ago: " + lockedPersons.size());

		List<Person> modifiedPersons = new ArrayList<>();
		lockedPersons.forEach(lockedPerson -> {
			if (lockedPerson.getMfaClients().stream().anyMatch(mfa -> mfa.getType() == ClientType.TOTPH)) {
				List<CachedMfaClient> totphDevices = lockedPerson.getMfaClients().stream().filter(d -> d.getType() == ClientType.TOTPH).collect(Collectors.toList());

				if (totphDevices != null && totphDevices.size() > 0) {
					// person is locked and has a TOTPH device - time to see if that person has other accounts
					List<Person> personsWithSameCpr = personService.getByCpr(lockedPerson.getCpr());

					// if they are all locked, then we can remove the TOTPH devices
					if (!personsWithSameCpr.stream().anyMatch(p -> !p.isLockedDataset())) {
						modifiedPersons.add(lockedPerson);
						List<String> deviceIds = totphDevices.stream().map(d -> d.getDeviceId()).collect(Collectors.toList());

						for (CachedMfaClient cachedMfaClient : totphDevices) {
							boolean success = deregisterHardwareToken(cachedMfaClient.getSerialnumber());
							if (!success) {
								log.warn("Unable to deregister " + cachedMfaClient.getSerialnumber());
								continue;
							}

							auditLogger.resetHardwareToken(cachedMfaClient.getSerialnumber(), null);
						}

						lockedPerson.getMfaClients().removeIf(c -> deviceIds.contains(c.getDeviceId()));
					}
				}
			}
		});
		
		if (!modifiedPersons.isEmpty()) {
			personService.saveAll(modifiedPersons);
		}
	}

	record MfaHistoryRecord(String serverName, String clientDeviceId, boolean clientAuthenticated, boolean clientRejected, LocalDateTime created, LocalDateTime sentTimestamp, boolean clientNotified, LocalDateTime clientFetchedTimestamp, LocalDateTime clientResponseTimestamp, String clientType, String systemName, String username) { }
	public List<MfaLoginHistory> fetchMfaLoginHistory(LocalDateTime after) {
		HttpHeaders headers = new HttpHeaders();
		headers.add("ApiKey", configuration.getMfa().getManagementApiKey());
		headers.add("connectorVersion", connectorVersion);
		HttpEntity<String> entity = new HttpEntity<>(headers);

		try {
			String url = configuration.getMfa().getBaseUrl() + "/api/login/mfa/history?after=" + after.toString();

			List<MfaLoginHistory> result = new ArrayList<>();
			ResponseEntity<MfaHistoryRecord[]> response = restTemplate.exchange(url, HttpMethod.GET, entity, MfaHistoryRecord[].class);
			for (MfaHistoryRecord mfaRecord : response.getBody()) {
				MfaLoginHistory entry = new MfaLoginHistory();
				
				entry.setClientType(mfaRecord.clientType());
				entry.setCreatedTts(mfaRecord.created());
				entry.setDeviceId(mfaRecord.clientDeviceId());
				entry.setFetchTts(mfaRecord.clientFetchedTimestamp());
				entry.setPushTts(mfaRecord.sentTimestamp());
				entry.setResponseTts(mfaRecord.clientResponseTimestamp());
				entry.setServerName(mfaRecord.serverName());
				entry.setUsername(mfaRecord.username());
				if (StringUtils.hasLength(mfaRecord.systemName())) {
					try {
						String rawString = URLDecoder.decode(mfaRecord.systemName(), Charset.forName("UTF-8"));
						byte[] raw = Base64.getDecoder().decode(rawString);
						entry.setSystemName(new String(raw, Charset.forName("UTF-8")));
					}
					catch (Exception ex) {
						log.warn("Failed to decode serverName = " + mfaRecord.systemName());
					}
				}
				
				String status = mfaRecord.clientAuthenticated()
						? "Godkendt"
						: (mfaRecord.clientRejected()
								? "Afvist"
								: (mfaRecord.clientFetchedTimestamp != null ? "Set" : "Ukendt")
						  );

				entry.setStatus(status);
				
				result.add(entry);
			}

			return result;
		}
		catch (Exception ex) {
			log.error("Failed to get mfa history", ex);
		}

		return null;
	}

	private record CodeOffsetBody(String serialnumber, String firstCode, String secondCode) { }
	public boolean adjustTOTPdrift(String serialnumber, String firstCode, String secondCode) {
		HttpHeaders headers = new HttpHeaders();
		headers.add("ApiKey", configuration.getMfa().getManagementApiKey());
		headers.add("connectorVersion", connectorVersion);
		
		CodeOffsetBody body = new CodeOffsetBody(serialnumber, firstCode, secondCode);
		HttpEntity<CodeOffsetBody> entity = new HttpEntity<>(body, headers);
		
		ResponseEntity<String> response = restTemplate.exchange(getURL("/api/login/kodeviser/adjust"), HttpMethod.POST, entity, String.class);
		return response.getStatusCode() == HttpStatus.OK;
	}
}
