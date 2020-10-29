package dk.digitalidentity.common.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.service.model.ADPasswordRequest;
import dk.digitalidentity.common.service.model.ADPasswordValidation;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ADPasswordService {

	// TODO: autowire RestTemplate

	@Autowired
	private CommonConfiguration configuration;

	public boolean validatePassword(Person person, String password) {
		String url = configuration.getAd().getBaseUrl();
		if (!url.endsWith("/")) {
			url += "/";
		}
		url += "api/validatePassword";

		try {
			RestTemplate restTemplate = new RestTemplate();
			ResponseEntity<ADPasswordValidation> response = restTemplate.exchange(url , HttpMethod.POST, getRequest(person, password), ADPasswordValidation.class);

			if (response.getStatusCodeValue() == 200) {
				ADPasswordValidation result = response.getBody();

				return result != null && result.isValid();
			}
		}
		catch (RestClientException ex) {
			log.warn("Failed to connect to AD Password validation service", ex);
		}

		log.warn("Could not validate password against AD for " + person.getSamaccountName());

		return false;
	}

	public boolean setPassword(Person person, String password) {
		String url = configuration.getAd().getBaseUrl();
		if (!url.endsWith("/")) {
			url += "/";
		}

		url += "api/setPassword";

		try {
			RestTemplate restTemplate = new RestTemplate();
			ResponseEntity<ADPasswordValidation> response = restTemplate.exchange(url , HttpMethod.POST, getRequest(person, password), ADPasswordValidation.class);
			
			if (response.getStatusCodeValue() == 200) {
				return true;
			}
		}
		catch (RestClientException ex) {
			log.error("Failed to connect to AD Password setting service", ex);
		}

		// TODO: in case of failure we need to alert both us and the customer (if they have configured an alert URL)
		
		return false;
	}

	private HttpEntity<ADPasswordRequest> getRequest(Person person, String password) {
		HttpHeaders headers = new HttpHeaders();
		headers.add("apiKey", configuration.getAd().getApiKey());

		return new HttpEntity<>(new ADPasswordRequest(person, password), headers);
	}
}
