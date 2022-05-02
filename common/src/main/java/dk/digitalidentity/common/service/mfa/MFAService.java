package dk.digitalidentity.common.service.mfa;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.LocalRegisteredMfaClient;
import dk.digitalidentity.common.dao.model.CachedMfaClient;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.service.LocalRegisteredMfaClientService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.mfa.model.ClientType;
import dk.digitalidentity.common.service.mfa.model.MFAClientDetails;
import dk.digitalidentity.common.service.mfa.model.MfaAuthenticationResponse;
import dk.digitalidentity.common.service.mfa.model.MfaClient;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MFAService {
	private static final String connectorVersion = "1.0.0";

	@Autowired
	@Qualifier("defaultRestTemplate")
	private RestTemplate restTemplate;

	@Autowired
	private CommonConfiguration configuration;
	
	@Autowired
	private LocalRegisteredMfaClientService localRegisteredMfaClientService;
	
	@Autowired
	private PersonService personService;

	public MFAClientDetails getClientDetails(String deviceId) {
		HttpHeaders headers = new org.springframework.http.HttpHeaders();
		headers.add("ApiKey", configuration.getMfa().getApiKey());
		HttpEntity<String> entity = new HttpEntity<>(headers);

		try {
			String url = configuration.getMfa().getBaseUrl() + "/api/server/nsis/" + deviceId + "/details";
			ResponseEntity<MFAClientDetails> response = restTemplate.postForEntity(url, entity, MFAClientDetails.class);
			if (!response.getStatusCode().is2xxSuccessful()) {
				return null;
			}
			
			return response.getBody();
		}
		catch (Exception ex) {
			log.error("Failed to get details for client: " + deviceId, ex);
		}

		return null;
	}

	public List<MfaClient> getClients(String cpr) {
		HttpHeaders headers = new HttpHeaders();
		headers.add("ApiKey", configuration.getMfa().getApiKey());
		headers.add("connectorVersion", connectorVersion);
		HttpEntity<String> entity = new HttpEntity<>(headers);

		try {
			String url = configuration.getMfa().getBaseUrl() + "/api/server/nsis/clients?ssn=" + encodeSsn(cpr);

			ResponseEntity<List<MfaClient>> response = restTemplate.exchange(url, HttpMethod.GET, entity, new ParameterizedTypeReference<List<MfaClient>>() { });
			List<MfaClient> mfaClients = response.getBody();
			List<String> mfaClientsDeviceIds = response.getBody().stream().map(m -> m.getDeviceId()).collect(Collectors.toList());

			List<LocalRegisteredMfaClient> localClients = localRegisteredMfaClientService.getByCpr(cpr);			
			for (LocalRegisteredMfaClient localClient : localClients) {
				if (!mfaClientsDeviceIds.contains(localClient.getDeviceId())) {
					MfaClient client = new MfaClient();
					client.setDeviceId(localClient.getDeviceId());
					client.setName(localClient.getName());
					client.setNsisLevel(localClient.getNsisLevel());
					client.setType(localClient.getType());
					client.setLocalClient(true);
					
					mfaClients.add(client);
				}
			}
			
			if (!configuration.getMfa().isAllowTotp() && mfaClients != null) {
				mfaClients = mfaClients.stream().filter(c -> !c.getType().equals(ClientType.TOTP)).collect(Collectors.toList());
			}

			// update cached MFA clients
			maintainCachedClients(cpr, mfaClients);
			
			return mfaClients;
		}
		catch (Exception ex) {
			log.error("Failed to get MFA clients for " + PersonService.maskCpr(cpr), ex);
		}

		return new ArrayList<MfaClient>();
	}

	public MfaClient getClient(String deviceId) {
		HttpHeaders headers = new HttpHeaders();
		headers.add("ApiKey", configuration.getMfa().getApiKey());
		headers.add("connectorVersion", connectorVersion);
		HttpEntity<String> entity = new HttpEntity<>(headers);

		try {
			String url = configuration.getMfa().getBaseUrl() + "/api/server/nsis/clients?deviceId=" + deviceId;

			ResponseEntity<List<MfaClient>> response = restTemplate.exchange(url, HttpMethod.GET, entity, new ParameterizedTypeReference<List<MfaClient>>() { });

			if (response.getBody().size() > 0) {
				return response.getBody().get(0);
			}
			
			return null;
		}
		catch (Exception ex) {
			log.error("Failed to get mfa client: " + ex);
		}

		return null;
	}

	private String encodeSsn(String ssn) throws Exception {
		if (ssn == null) {
			return null;
		}

		// remove slashes
		ssn = ssn.replace("-", "");

		// digest
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		byte[] ssnDigest = md.digest(ssn.getBytes(Charset.forName("UTF-8")));

		// base64 encode
		return Base64.getEncoder().encodeToString(ssnDigest);
	}

	public List<MfaAuthenticationResponse> authenticateWithCpr(String cpr) {
		List<MfaAuthenticationResponse> response = new ArrayList<>();

		List<MfaClient> clients = getClients(cpr);
		if (clients == null || clients.size() == 0) {
			return response;
		}

		// filter out yubikeys
		clients = clients.stream().filter(c -> !ClientType.YUBIKEY.equals(c.getType())).collect(Collectors.toList());
		
		// check for prime
		MfaClient primeClient = clients.stream().filter(c -> c.isPrime()).findFirst().orElse(null);
		if (primeClient != null) {
			clients = Collections.singletonList(primeClient);
		}

		HttpHeaders headers = new HttpHeaders();
		headers.add("ApiKey", configuration.getMfa().getApiKey());
		headers.add("connectorVersion", connectorVersion);
		HttpEntity<String> entity = new HttpEntity<>(headers);

		for (MfaClient client : clients) {
			try {
				String url = configuration.getMfa().getBaseUrl() + "/api/server/client/" + client.getDeviceId() + "/authenticate?emitChallenge=false";

				ResponseEntity<MfaAuthenticationResponse> result = restTemplate.exchange(url, HttpMethod.PUT, entity, new ParameterizedTypeReference<MfaAuthenticationResponse>() { });
				response.add(result.getBody());
			}
			catch (Exception ex) {
				log.error("Failed initialise authentication: " + ex);
			}			
		}
		
		return response;
	}

	public MfaAuthenticationResponse authenticate(String deviceId) {
		HttpHeaders headers = new HttpHeaders();
		headers.add("ApiKey", configuration.getMfa().getApiKey());
		headers.add("connectorVersion", connectorVersion);
		HttpEntity<String> entity = new HttpEntity<>(headers);

		try {
			String url = configuration.getMfa().getBaseUrl() + "/api/server/client/" + deviceId + "/authenticate";

			ResponseEntity<MfaAuthenticationResponse> response = restTemplate.exchange(url, HttpMethod.PUT, entity, new ParameterizedTypeReference<MfaAuthenticationResponse>() { });
			return response.getBody();
		}
		catch (Exception ex) {
			log.error("Failed initialise authentication: " + ex);
			return null;
		}		
	}

	public boolean isAuthenticated(String subscriptionKey, Person person) {
		HttpHeaders headers = new HttpHeaders();
		headers.add("ApiKey", configuration.getMfa().getApiKey());
		headers.add("connectorVersion", connectorVersion);
		HttpEntity<String> entity = new HttpEntity<>(headers);

		try {
			String url = configuration.getMfa().getBaseUrl() + "/api/server/notification/" + subscriptionKey + "/status";

			ResponseEntity<MfaAuthenticationResponse> response = restTemplate.exchange(url, HttpMethod.GET, entity, new ParameterizedTypeReference<MfaAuthenticationResponse>() { });
			MfaAuthenticationResponse responseBody = response.getBody();

			if (responseBody != null) {
				return responseBody.isClientAuthenticated();
			}
		}
		catch (Exception ex) {
			log.error("Failed to get mfa auth status for person with uuid " + person.getUuid() + ", and subscriptionKey = " + subscriptionKey, ex);
			return false;
		}

		return false;
	}

	public MfaAuthenticationResponse getMfaAuthenticationResponse(String subscriptionKey, Person person) {
		HttpHeaders headers = new HttpHeaders();
		headers.add("ApiKey", configuration.getMfa().getApiKey());
		headers.add("connectorVersion", connectorVersion);
		HttpEntity<String> entity = new HttpEntity<>(headers);

		try {
			String url = configuration.getMfa().getBaseUrl() + "/api/server/notification/" + subscriptionKey + "/status";

			ResponseEntity<MfaAuthenticationResponse> response = restTemplate.exchange(url, HttpMethod.GET, entity, new ParameterizedTypeReference<MfaAuthenticationResponse>() { });

			return response.getBody();
		}
		catch (Exception ex) {
			log.error("Failed to get mfa auth status for person with uuid " + person.getUuid() + ": " + ex);
			return null;
		}
	}
	
	private void maintainCachedClients(String cpr, List<MfaClient> mfaClients) {
		List<Person> persons = personService.getByCpr(cpr);

		for (Person person : persons) {
			if (person.getMfaClients() == null) {
				// this case should never happen - Hibernate will make sure we get an empty collection
				List<CachedMfaClient> newCachedMFAClients = toCachedMFAClients(mfaClients, person);
				person.setMfaClients(newCachedMFAClients);

				personService.save(person);
			}
			else if (person.getMfaClients().size() == 0) {
				person.getMfaClients().addAll(toCachedMFAClients(mfaClients, person));
			}
			else {
				List<String> cachedMFAClientDeviceIds = person.getMfaClients().stream().map(c -> c.getDeviceId()).collect(Collectors.toList());
				List<String> mfaClientDeviceIds = mfaClients.stream().map(c -> c.getDeviceId()).collect(Collectors.toList());
				
				List<MfaClient> toCreate = mfaClients.stream().filter(m -> !cachedMFAClientDeviceIds.contains(m.getDeviceId())).collect(Collectors.toList());
				List<CachedMfaClient> toDelete = person.getMfaClients().stream().filter(m -> !mfaClientDeviceIds.contains(m.getDeviceId())).collect(Collectors.toList());
				
				boolean changes = false;
				
				for (MfaClient mfaClient : mfaClients) {
					if (cachedMFAClientDeviceIds.contains(mfaClient.getDeviceId())) {
						
						// update case
						CachedMfaClient cachedClientToUpdate = person.getMfaClients().stream().filter(c -> c.getDeviceId().equals(mfaClient.getDeviceId())).findAny().orElse(null);
						if (cachedClientToUpdate == null) {
							continue;
						}
						
						// name cannot currently change in OS2faktor MFA, but let's support it here
						if (!Objects.equals(mfaClient.getName(), cachedClientToUpdate.getName())) {
							changes = true;
							cachedClientToUpdate.setName(mfaClient.getName());
						}

						// NSISLevel cannot currently change in OS2faktor MFA, but let's support it here
						if (!Objects.equals(mfaClient.getNsisLevel(), cachedClientToUpdate.getNsisLevel())) {
							changes = true;
							cachedClientToUpdate.setNsisLevel(mfaClient.getNsisLevel());
						}
					}
				}
				
				if (!toCreate.isEmpty()) {
					changes = true;
					person.getMfaClients().addAll(toCachedMFAClients(toCreate, person));
				}
				
				if (!toDelete.isEmpty()) {
					changes = true;
					person.getMfaClients().removeAll(toDelete);
				}

				if (changes) {
					personService.save(person);
				}
			}
		}
	}

	private List<CachedMfaClient> toCachedMFAClients(List<MfaClient> mfaClients, Person person) {
		List<CachedMfaClient> newCachedMFAClients = new ArrayList<>();
		
		for (MfaClient client : mfaClients) {
			CachedMfaClient cachedClient = new CachedMfaClient();
			cachedClient.setDeviceId(client.getDeviceId());
			cachedClient.setName(client.getName());
			cachedClient.setType(client.getType());
			cachedClient.setNsisLevel(client.getNsisLevel());
			cachedClient.setPerson(person);

			newCachedMFAClients.add(cachedClient);
		}
		
		return newCachedMFAClients;
	}
}