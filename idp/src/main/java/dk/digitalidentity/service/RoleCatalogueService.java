package dk.digitalidentity.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.service.model.OIOBPP;
import dk.digitalidentity.service.model.RoleCatalogueRolesResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RoleCatalogueService {

	@Autowired
	private OS2faktorConfiguration configuration;

	public String getNameId(Person person) {
		RestTemplate restTemplate = new RestTemplate();

		String roleCatalogueUrl = configuration.getRoleCatalogue().getBaseUrl();
		if (!roleCatalogueUrl.endsWith("/")) {
			roleCatalogueUrl += "/";
		}
		roleCatalogueUrl += "api/user/" + person.getUuid() + "/nameid";

		HttpHeaders headers = new HttpHeaders();
		headers.add("ApiKey", configuration.getRoleCatalogue().getApiKey());

		HttpEntity<String> request = new HttpEntity<>(headers);

		ResponseEntity<String> response = restTemplate.exchange(roleCatalogueUrl, HttpMethod.GET, request, new ParameterizedTypeReference<>() {});

		return response.getBody();
	}

	public String getOIOBPP(Person person, String system) {
		try {
			RestTemplate restTemplate = new RestTemplate();
	
			String roleCatalogueUrl = configuration.getRoleCatalogue().getBaseUrl();
			if (!roleCatalogueUrl.endsWith("/")) {
				roleCatalogueUrl += "/";
			}
			
			if (!StringUtils.isEmpty(person.getSamaccountName())) {
				roleCatalogueUrl += "api/user/" + person.getSamaccountName() + "/roles";
			}
			else {
				roleCatalogueUrl += "/api/user/" + person.getUuid() + "/roles";
			}
	
			HttpHeaders headers = new HttpHeaders();
			headers.add("ApiKey", configuration.getRoleCatalogue().getApiKey());
	
			HttpEntity<OIOBPP> request = new HttpEntity<>(headers);
			UriComponentsBuilder urlParamBuilder = UriComponentsBuilder.fromHttpUrl(roleCatalogueUrl).queryParam("system", system);
	
			ResponseEntity<OIOBPP> response = restTemplate.exchange(urlParamBuilder.toUriString(), HttpMethod.GET, request, new ParameterizedTypeReference<>() {});
	
			OIOBPP oiobpp = response.getBody();
	
			return oiobpp != null ? oiobpp.getOioBPP() : null;
		}
		catch (Exception ex) {
			log.error("Failed to connect to role catalogue", ex);

			return null;
		}
	}

	public List<String> getSystemRoles(Person person, String itSystem) {
		try {
			RestTemplate restTemplate = new RestTemplate();
	
			String roleCatalogueUrl = configuration.getRoleCatalogue().getBaseUrl();
			if (!roleCatalogueUrl.endsWith("/")) {
				roleCatalogueUrl += "/";
			}
			
			if (!StringUtils.isEmpty(person.getSamaccountName())) {
				roleCatalogueUrl += "api/user/" + person.getSamaccountName() + "/rolesAsList";
			}
			else {
				roleCatalogueUrl += "/api/user/" + person.getUuid() + "/rolesAsList";
			}
	
			HttpHeaders headers = new HttpHeaders();
			headers.add("ApiKey", configuration.getRoleCatalogue().getApiKey());
	
			HttpEntity<String> request = new HttpEntity<>(headers);
			UriComponentsBuilder urlParamBuilder = UriComponentsBuilder.fromHttpUrl(roleCatalogueUrl).queryParam("system", itSystem);
	
			ResponseEntity<RoleCatalogueRolesResponse> response = restTemplate.exchange(urlParamBuilder.toUriString(), HttpMethod.GET, request, new ParameterizedTypeReference<>() {});
	
			RoleCatalogueRolesResponse res = response.getBody();
	
			return res != null ? res.getSystemRoles() : null;
		}
		catch (Exception ex) {
			log.error("Failed to connect to role catalogue", ex);

			return null;
		}
	}
}
