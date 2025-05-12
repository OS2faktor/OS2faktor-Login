package dk.digitalidentity.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.CachedMfaClient;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.CachedMfaClientService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.mfa.model.ClientType;
import dk.digitalidentity.common.service.mfa.model.HardwareTokenDTO;
import dk.digitalidentity.service.dto.AuthenticateUserRequestBody;
import dk.digitalidentity.service.dto.MfaRenameRequestDTO;
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
	private CachedMfaClientService cachedMfaClientService;
	
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
	
	@Transactional
	public void removeTOTPHDevicesOnLockedPersons() {
		List<Person> lockedPersons = personService.findByLockedDatasetTrue();

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

		List<Long> modifiedPersonsId = new ArrayList<>();
		List<Person> modifiedPersons = new ArrayList<>();
		lockedPersons.forEach(p -> {
			if (p.getMfaClients().stream().anyMatch(mfa -> mfa.getType() == ClientType.TOTPH)) {
				List<CachedMfaClient> totphDevices = p.getMfaClients().stream().filter(d -> d.getType() == ClientType.TOTPH).collect(Collectors.toList());
				
				for (CachedMfaClient cachedMfaClient : totphDevices) {
					boolean success = deregisterHardwareToken(cachedMfaClient.getSerialnumber());
					if (!success) {
						log.warn("Unable to deregister " + cachedMfaClient.getSerialnumber());
						continue;
					}

					cachedMfaClientService.deleteBySerialnumber(cachedMfaClient.getSerialnumber());

					auditLogger.resetHardwareToken(cachedMfaClient.getSerialnumber(), null);

					if (!modifiedPersonsId.contains(p.getId())) {
						modifiedPersonsId.add(p.getId());
						modifiedPersons.add(p);
					}
				}
			}
		});
		
		if (!modifiedPersons.isEmpty()) {
			personService.saveAll(modifiedPersons);
		}
	}
}