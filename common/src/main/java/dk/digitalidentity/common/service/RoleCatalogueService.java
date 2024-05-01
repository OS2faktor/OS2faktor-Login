package dk.digitalidentity.common.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
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

	public String getNemLoginOIOBPP(Person person) {
		if (!configuration.getRoleCatalogue().isEnabled()) {
			log.error("RoleCatalogue not enabled - oiobpp is null");
			return null;
		}

		try {
			RestTemplate restTemplate = new RestTemplate();
	
			String roleCatalogueUrl = configuration.getRoleCatalogue().getBaseUrl();
			if (StringUtils.hasLength(person.getSamaccountName())) {
				roleCatalogueUrl += "api/user/" + person.getSamaccountName() + "/nemloginRoles";
			}
			else {
				roleCatalogueUrl += "api/user/" + person.getUuid() + "/nemloginRoles";
			}
	
			HttpHeaders headers = new HttpHeaders();
			headers.add("ApiKey", configuration.getRoleCatalogue().getApiKey());
	
			HttpEntity<OIOBPP> request = new HttpEntity<>(headers);
			UriComponentsBuilder urlParamBuilder = UriComponentsBuilder.fromHttpUrl(roleCatalogueUrl);
	
			ResponseEntity<OIOBPP> response = restTemplate.exchange(urlParamBuilder.toUriString(), HttpMethod.GET, request, new ParameterizedTypeReference<>() {});

			OIOBPP oiobpp = response.getBody();
	
			return oiobpp != null ? oiobpp.getOioBPP() : null;
		}
		catch (Exception ex) {
			if (ex instanceof HttpStatusCodeException) {
				HttpStatusCodeException httpStatusException = (HttpStatusCodeException) ex;
				if (httpStatusException.getStatusCode() == HttpStatus.NOT_FOUND) {
					log.warn("User not found for PersonID: " + person.getId());
					return null;
				}
			}

			log.error("Failed to connect to role catalogue", ex);
			return null;
		}
	}
	
	public String getOIOBPP(Person person, String system) {
		if (!configuration.getRoleCatalogue().isEnabled()) {
			log.error("RoleCatalogue not enabled - oiobpp is null");
			return null;
		}

		try {
			RestTemplate restTemplate = new RestTemplate();
	
			String roleCatalogueUrl = configuration.getRoleCatalogue().getBaseUrl();
			if (StringUtils.hasLength(person.getSamaccountName())) {
				roleCatalogueUrl += "api/user/" + person.getSamaccountName() + "/roles";
			}
			else {
				roleCatalogueUrl += "api/user/" + person.getUuid() + "/roles";
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
			if (ex instanceof HttpStatusCodeException) {
				HttpStatusCodeException httpStatusException = (HttpStatusCodeException) ex;
				if (httpStatusException.getStatusCode() == HttpStatus.NOT_FOUND) {
					log.warn("User not found for PersonID: " + person.getId());
					return null;
				}
			}

			log.error("Failed to connect to role catalogue", ex);
			return null;
		}
	}

	public List<String> getSystemRoles(Person person, String itSystem) {
		if (!configuration.getRoleCatalogue().isEnabled()) {
			log.error("RoleCatalogue not enabled - systemRoles is null");
			return new ArrayList<>();
		}

		RoleCatalogueRolesResponse response = lookupRoles(person, itSystem);
		if (response == null) {
			return new ArrayList<>();
		}
		
		return response.getSystemRoles();
	}
	
	public List<String> getUserRoles(Person person, String itSystem) {
		if (!configuration.getRoleCatalogue().isEnabled()) {
			log.error("RoleCatalogue not enabled - userRoles is null");
			return new ArrayList<>();
		}

		RoleCatalogueRolesResponse response = lookupRoles(person, itSystem);
		if (response == null) {
			return new ArrayList<>();
		}
		
		return response.getUserRoles();
	}
	
	public String getSystemRolesAsOIOBPP(Person person, String itSystem) {
		if (!configuration.getRoleCatalogue().isEnabled()) {
			log.error("RoleCatalogue not enabled - oiobpp/systemroles is null");
			return null;
		}

		RoleCatalogueOIOBPPResponse response = lookupRolesAsOIOBPP(person, itSystem);
		if (response == null) {
			return null;
		}
		
		return response.getOioBPP();
	}

	public boolean hasUserRole(Person person, String userRoleId) {
		if (!configuration.getRoleCatalogue().isEnabled()) {
			log.error("RoleCatalogue not enabled - hasUserRole is always false");
			return false;
		}

		try {
			RestTemplate restTemplate = new RestTemplate();
	
			String roleCatalogueUrl = configuration.getRoleCatalogue().getBaseUrl();
			roleCatalogueUrl += "api/user/" + person.getSamaccountName() + "/hasUserRole/" + userRoleId;
	
			HttpHeaders headers = new HttpHeaders();
			headers.add("ApiKey", configuration.getRoleCatalogue().getApiKey());
	
			HttpEntity<String> request = new HttpEntity<>(headers);
	
			ResponseEntity<String> response = restTemplate.exchange(roleCatalogueUrl, HttpMethod.GET, request, new ParameterizedTypeReference<>() {});
			if (response.getStatusCodeValue() == 404) {
				return false;
			}
			else if (response.getStatusCodeValue() != 200) {
				log.error("Failed to lookup userRoles for " + person.getSamaccountName() + " return code " + response.getStatusCodeValue());
				return false;
			}
			
			return true;
		}
		catch (Exception ex) {
			if (ex instanceof HttpStatusCodeException) {
				HttpStatusCodeException httpStatusException = (HttpStatusCodeException) ex;
				if (httpStatusException.getStatusCode() == HttpStatus.NOT_FOUND) {
					return false;
				}
			}

			log.error("Failed to connect to role catalogue", ex);
		}
		
		return false;
	}

	public boolean hasSystemRole(Person person, String systemRoleId) {
		if (!configuration.getRoleCatalogue().isEnabled()) {
			log.error("RoleCatalogue not enabled - hasSystemRole is always false");
			return false;
		}

		try {
			RestTemplate restTemplate = new RestTemplate();
	
			String roleCatalogueUrl = configuration.getRoleCatalogue().getBaseUrl();
			roleCatalogueUrl += "api/user/" + person.getSamaccountName() + "/hasSystemRole/" + systemRoleId;
	
			HttpHeaders headers = new HttpHeaders();
			headers.add("ApiKey", configuration.getRoleCatalogue().getApiKey());
	
			HttpEntity<String> request = new HttpEntity<>(headers);
	
			ResponseEntity<String> response = restTemplate.exchange(roleCatalogueUrl, HttpMethod.GET, request, new ParameterizedTypeReference<>() {});
			if (response.getStatusCodeValue() == 404) {
				return false;
			}
			else if (response.getStatusCodeValue() != 200) {
				log.error("Failed to lookup systemRoles for " + person.getSamaccountName() + " return code " + response.getStatusCodeValue());
				return false;
			}
			
			return true;
		}
		catch (Exception ex) {
			if (ex instanceof HttpStatusCodeException) {
				HttpStatusCodeException httpStatusException = (HttpStatusCodeException) ex;
				if (httpStatusException.getStatusCode() == HttpStatus.NOT_FOUND) {
					return false;
				}
			}

			log.error("Failed to connect to role catalogue", ex);
		}
		
		return false;
	}
	
	private RoleCatalogueOIOBPPResponse lookupRolesAsOIOBPP(Person person, String itSystem) {
		try {
			RestTemplate restTemplate = new RestTemplate();
	
			String roleCatalogueUrl = configuration.getRoleCatalogue().getBaseUrl();
			if (StringUtils.hasLength(person.getSamaccountName())) {
				roleCatalogueUrl += "api/user/" + person.getSamaccountName() + "/roles";
			}
			else {
				roleCatalogueUrl += "api/user/" + person.getUuid() + "/roles";
			}
	
			HttpHeaders headers = new HttpHeaders();
			headers.add("ApiKey", configuration.getRoleCatalogue().getApiKey());
	
			HttpEntity<String> request = new HttpEntity<>(headers);
			UriComponentsBuilder urlParamBuilder = UriComponentsBuilder.fromHttpUrl(roleCatalogueUrl).queryParam("system", itSystem);
	
			ResponseEntity<RoleCatalogueOIOBPPResponse> response = restTemplate.exchange(urlParamBuilder.toUriString(), HttpMethod.GET, request, new ParameterizedTypeReference<>() {});
	
			return response.getBody();
		}
		catch (Exception ex) {
			if (ex instanceof HttpStatusCodeException) {
				HttpStatusCodeException httpStatusException = (HttpStatusCodeException) ex;
				if (httpStatusException.getStatusCode() == HttpStatus.NOT_FOUND) {
					log.warn("User not found for PersonID: " + person.getId());
					return null;
				}
			}

			log.error("Failed to connect to role catalogue", ex);
			return null;
		}
	}

	private RoleCatalogueRolesResponse lookupRoles(Person person, String itSystem) {
		try {
			RestTemplate restTemplate = new RestTemplate();
	
			String roleCatalogueUrl = configuration.getRoleCatalogue().getBaseUrl();
			if (StringUtils.hasLength(person.getSamaccountName())) {
				roleCatalogueUrl += "api/user/" + person.getSamaccountName() + "/rolesAsList";
			}
			else {
				roleCatalogueUrl += "api/user/" + person.getUuid() + "/rolesAsList";
			}
	
			HttpHeaders headers = new HttpHeaders();
			headers.add("ApiKey", configuration.getRoleCatalogue().getApiKey());
	
			HttpEntity<String> request = new HttpEntity<>(headers);
			UriComponentsBuilder urlParamBuilder = UriComponentsBuilder.fromHttpUrl(roleCatalogueUrl).queryParam("system", itSystem);
	
			ResponseEntity<RoleCatalogueRolesResponse> response = restTemplate.exchange(urlParamBuilder.toUriString(), HttpMethod.GET, request, new ParameterizedTypeReference<>() {});

			return response.getBody();
		}
		catch (Exception ex) {
			if (ex instanceof HttpStatusCodeException) {
				HttpStatusCodeException httpStatusException = (HttpStatusCodeException) ex;
				if (httpStatusException.getStatusCode() == HttpStatus.NOT_FOUND) {
					log.warn("User not found for PersonID: " + person.getId());
					return null;
				}
			}

			log.error("Failed to connect to role catalogue", ex);
			return null;
		}
	}
}
