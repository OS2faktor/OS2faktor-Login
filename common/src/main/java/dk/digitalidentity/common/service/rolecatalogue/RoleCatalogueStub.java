package dk.digitalidentity.common.service.rolecatalogue;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.service.model.OIOBPP;
import dk.digitalidentity.common.service.model.RoleCatalogueOIOBPPResponse;
import dk.digitalidentity.common.service.model.RoleCatalogueRolesResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@EnableCaching
class RoleCatalogueStub {

	@Autowired
	private CommonConfiguration configuration;
	
	@CacheEvict(value = { "lookupRolesCache" }, allEntries = true)
	void cacheClear() {
		;
	}

	@CacheEvict(value = { "extendedSystemRolesCache", "allItSystemsCache" }, allEntries = true)
	void slowCacheClear() {
		;
	}

	String getOIOBPP(String samAccountName, String system) {
		if (!configuration.getRoleCatalogue().isEnabled()) {
			log.error("RoleCatalogue not enabled - oiobpp is null");
			return null;
		}

		try {
			RestTemplate restTemplate = new RestTemplate();
	
			String roleCatalogueUrl = configuration.getRoleCatalogue().getBaseUrl();
			roleCatalogueUrl += "api/user/" + samAccountName + "/roles";
	
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
					log.warn("User not found: " + samAccountName);
					return null;
				}
			}

			log.error("Failed to connect to role catalogue", ex);
			return null;
		}
	}

	boolean hasUserRole(String samAccountName, String userRoleId) {
		if (!configuration.getRoleCatalogue().isEnabled()) {
			log.error("RoleCatalogue not enabled - hasUserRole is always false");
			return false;
		}

		try {
			RestTemplate restTemplate = new RestTemplate();
	
			String roleCatalogueUrl = configuration.getRoleCatalogue().getBaseUrl();
			roleCatalogueUrl += "api/user/" + samAccountName + "/hasUserRole/" + userRoleId;
	
			HttpHeaders headers = new HttpHeaders();
			headers.add("ApiKey", configuration.getRoleCatalogue().getApiKey());
	
			HttpEntity<String> request = new HttpEntity<>(headers);
	
			ResponseEntity<String> response = restTemplate.exchange(roleCatalogueUrl, HttpMethod.GET, request, new ParameterizedTypeReference<>() {});
			if (response.getStatusCode().value() == 404) {
				return false;
			}
			else if (response.getStatusCode().value() != 200) {
				log.error("Failed to lookup userRoles for " + samAccountName + " return code " + response.getStatusCode().value());
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

	boolean hasSystemRole(String samAccountName, String systemRoleId) {
		if (!configuration.getRoleCatalogue().isEnabled()) {
			log.error("RoleCatalogue not enabled - hasSystemRole is always false");
			return false;
		}

		try {
			RestTemplate restTemplate = new RestTemplate();
	
			String roleCatalogueUrl = configuration.getRoleCatalogue().getBaseUrl();
			roleCatalogueUrl += "api/user/" + samAccountName + "/hasSystemRole?roleIdentifier=" + systemRoleId;
	
			HttpHeaders headers = new HttpHeaders();
			headers.add("ApiKey", configuration.getRoleCatalogue().getApiKey());
	
			HttpEntity<String> request = new HttpEntity<>(headers);
	
			ResponseEntity<String> response = restTemplate.exchange(roleCatalogueUrl, HttpMethod.GET, request, new ParameterizedTypeReference<>() {});
			if (response.getStatusCode().value() == 404) {
				return false;
			}
			else if (response.getStatusCode().value() != 200) {
				log.error("Failed to lookup systemRoles for " + samAccountName + " return code " + response.getStatusCode().value());
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

	RoleCatalogueOIOBPPResponse lookupRolesAsOIOBPP(String samAccountName, String itSystem) {
		try {
			RestTemplate restTemplate = new RestTemplate();
	
			String roleCatalogueUrl = configuration.getRoleCatalogue().getBaseUrl();
			roleCatalogueUrl += "api/user/" + samAccountName + "/roles";
	
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
					log.warn("User not found: " + samAccountName);
					return null;
				}
			}

			log.error("Failed to connect to role catalogue", ex);
			return null;
		}
	}

	@Cacheable("lookupRolesCache")
	RoleCatalogueRolesResponse lookupRoles(String samAccountName, String itSystem) {
		try {
			RestTemplate restTemplate = new RestTemplate();
	
			String roleCatalogueUrl = configuration.getRoleCatalogue().getBaseUrl();
			roleCatalogueUrl += "api/user/" + samAccountName + "/rolesAsList";
	
			HttpHeaders headers = new HttpHeaders();
			headers.add("ApiKey", configuration.getRoleCatalogue().getApiKey());
	
			HttpEntity<String> request = new HttpEntity<>(headers);
			UriComponentsBuilder urlParamBuilder = UriComponentsBuilder.fromHttpUrl(roleCatalogueUrl).queryParam("system", itSystem);
	
			ResponseEntity<RoleCatalogueRolesResponse> response = restTemplate.exchange(urlParamBuilder.toUriString(), HttpMethod.GET, request, new ParameterizedTypeReference<>() {});

			return response.getBody();
		}
		catch (Exception ex) {
			if (ex instanceof HttpStatusCodeException httpStatusException && httpStatusException.getStatusCode() == HttpStatus.NOT_FOUND) {
				log.warn("User not found: " + samAccountName);
				return null;
			}

			log.error("Failed to connect to role catalogue", ex);
			return null;
		}
	}
	
	record SystemRoleRecord(Long id, String name, String identifier) {}
	record ItSystemRecord(Long id, String name, String identifier) {}
	
	@Cacheable("allItSystemsCache")
	List<ItSystemRecord> getAllItSystems() {
		try {
			RestTemplate restTemplate = new RestTemplate();

			String roleCatalogueUrl = configuration.getRoleCatalogue().getBaseUrl();
			roleCatalogueUrl += "/api/v2/itsystem";

			HttpHeaders headers = new HttpHeaders();
			headers.add("ApiKey", configuration.getRoleCatalogue().getApiKey());

			HttpEntity<String> request = new HttpEntity<>(headers);

			ResponseEntity<List<ItSystemRecord>> response = restTemplate.exchange(roleCatalogueUrl, HttpMethod.GET, request, new ParameterizedTypeReference<>() {});

			return response.getBody();
		}
		catch (Exception ex) {
			if (ex instanceof HttpStatusCodeException httpStatusException && httpStatusException.getStatusCode() == HttpStatus.NOT_FOUND) {
				log.warn("No itSystems were found.");
				return new ArrayList<>();
			}

			log.error("Failed to connect to role catalogue", ex);
			return new ArrayList<>();
		}
	}

	@Cacheable("extendedSystemRolesCache")
	List<SystemRoleRecord> getExtendedSystemRoles(long itSystemId) {
		try {
			RestTemplate restTemplate = new RestTemplate();

			String roleCatalogueUrl = configuration.getRoleCatalogue().getBaseUrl();
			roleCatalogueUrl += "/api/v2/itsystem/" + itSystemId + "/systemroles";

			HttpHeaders headers = new HttpHeaders();
			headers.add("ApiKey", configuration.getRoleCatalogue().getApiKey());

			HttpEntity<String> request = new HttpEntity<>(headers);

			ResponseEntity<List<SystemRoleRecord>> response = restTemplate.exchange(roleCatalogueUrl, HttpMethod.GET, request, new ParameterizedTypeReference<>() {});

			return response.getBody();
		}
		catch (Exception ex) {
			if (ex instanceof HttpStatusCodeException httpStatusException && httpStatusException.getStatusCode() == HttpStatus.NOT_FOUND) {
				log.warn("ItSystem or SystemRoles not found for ItSystemId: " + itSystemId);
				return new ArrayList<>();
			}

			log.error("Failed to connect to role catalogue", ex);
			return new ArrayList<>();
		}
	}
}
