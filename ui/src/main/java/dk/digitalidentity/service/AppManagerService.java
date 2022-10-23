package dk.digitalidentity.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.service.dto.ApplicationApiDTO;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AppManagerService {

	@Autowired
	private OS2faktorConfiguration configuration;

	public List<ApplicationApiDTO> getApplications() {
		RestTemplate restTemplate = new RestTemplate();
		
		String appManagerUrl = configuration.getAppManager().getUrl();
		if (!appManagerUrl.endsWith("/")) {
			appManagerUrl += "/";
		}

		appManagerUrl += "applications";

		HttpEntity<String> request = new HttpEntity<>(getHeaders());
		try {
			ResponseEntity<List<ApplicationApiDTO>> response = restTemplate.exchange(appManagerUrl, HttpMethod.GET, request, new ParameterizedTypeReference<List<ApplicationApiDTO>>() {});
			
			return response.getBody();
		}
		catch (RestClientException ex) {
			log.warn("Failed to call AppManager API.", ex);
			return null;
		}
	}

	private HttpHeaders getHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json");
		
		return headers;
	}
}
