package dk.digitalidentity.common.service;

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

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.service.model.OIOBPP;
import dk.digitalidentity.common.service.model.RoleCatalogueOIOBPPResponse;
import dk.digitalidentity.common.service.model.RoleCatalogueRolesResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RoleCatalogueService {

	@Autowired
	private CommonConfiguration configuration;

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
			
			if (StringUtils.hasLength(person.getSamaccountName())) {
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
		RoleCatalogueRolesResponse response = lookupRoles(person, itSystem);
		if (response == null) {
			return null;
		}
		
		return response.getSystemRoles();
	}
	
	public List<String> getUserRoles(Person person, String itSystem) {
		RoleCatalogueRolesResponse response = lookupRoles(person, itSystem);
		if (response == null) {
			return null;
		}
		
		return response.getUserRoles();
	}
	
	public String getSystemRolesAsOIOBPP(Person person, String itSystem) {
		RoleCatalogueOIOBPPResponse response = lookupRolesAsOIOBPP(person, itSystem);
		if (response == null) {
			return null;
		}
		
		return response.getOioBPP();
	}

	// TODO: implement this
	public boolean hasUserRole(Person person, String userRoleId) {
		log.error("hasUserRole not implemented on RC yet - waiting for code there...");
		return false;
	}

	// TODO: implement this
	public boolean hasSystemRole(Person person, String systemRoleId) {
		log.error("hasSystemRole not implemented on RC yet - waiting for code there...");
		return false;
	}
	
	private RoleCatalogueOIOBPPResponse lookupRolesAsOIOBPP(Person person, String itSystem) {
		try {
			RestTemplate restTemplate = new RestTemplate();
	
			String roleCatalogueUrl = configuration.getRoleCatalogue().getBaseUrl();
			if (!roleCatalogueUrl.endsWith("/")) {
				roleCatalogueUrl += "/";
			}
			
			if (StringUtils.hasLength(person.getSamaccountName())) {
				roleCatalogueUrl += "api/user/" + person.getSamaccountName() + "/roles";
			}
			else {
				roleCatalogueUrl += "/api/user/" + person.getUuid() + "/roles";
			}
	
			HttpHeaders headers = new HttpHeaders();
			headers.add("ApiKey", configuration.getRoleCatalogue().getApiKey());
	
			HttpEntity<String> request = new HttpEntity<>(headers);
			UriComponentsBuilder urlParamBuilder = UriComponentsBuilder.fromHttpUrl(roleCatalogueUrl).queryParam("system", itSystem);
	
			ResponseEntity<RoleCatalogueOIOBPPResponse> response = restTemplate.exchange(urlParamBuilder.toUriString(), HttpMethod.GET, request, new ParameterizedTypeReference<>() {});
	
			return response.getBody();
		}
		catch (Exception ex) {
			log.error("Failed to connect to role catalogue", ex);

			return null;
		}
	}

	private RoleCatalogueRolesResponse lookupRoles(Person person, String itSystem) {
		try {
			RestTemplate restTemplate = new RestTemplate();
	
			String roleCatalogueUrl = configuration.getRoleCatalogue().getBaseUrl();
			if (!roleCatalogueUrl.endsWith("/")) {
				roleCatalogueUrl += "/";
			}
			
			if (StringUtils.hasLength(person.getSamaccountName())) {
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
	
			return response.getBody();
		}
		catch (Exception ex) {
			log.error("Failed to connect to role catalogue", ex);

			return null;
		}
	}
}
