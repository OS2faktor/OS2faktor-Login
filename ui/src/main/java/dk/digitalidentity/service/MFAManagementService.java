package dk.digitalidentity.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.service.mfa.model.HardwareTokenDTO;
import dk.digitalidentity.service.dto.MfaRenameRequestDTO;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class MFAManagementService {
	private static final String connectorVersion = "1.0.0";

	@Autowired
	private CommonConfiguration configuration;

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
			if (response.getStatusCodeValue() == 200) {
				return true;
			}
			
			log.error("Failed to rename client to '" + name + "' for deviceId = " + deviceId + " with statusCode = " + response.getStatusCodeValue());
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
}
