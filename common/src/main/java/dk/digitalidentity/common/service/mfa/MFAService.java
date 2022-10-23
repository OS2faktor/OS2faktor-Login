package dk.digitalidentity.common.service.mfa;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.spec.KeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.CachedMfaClient;
import dk.digitalidentity.common.dao.model.LocalRegisteredMfaClient;
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
	private static final String connectorVersion = "nsis-1.0.0";
	private IvParameterSpec iv;
	private SecretKey encryptionKey;

	@Autowired
	@Qualifier("defaultRestTemplate")
	private RestTemplate restTemplate;

	@Autowired(required = false)
	@Qualifier("mfaTemplate")
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private CommonConfiguration configuration;
	
	@Autowired
	private LocalRegisteredMfaClientService localRegisteredMfaClientService;
	
	@Autowired
	private PersonService personService;
	
	@PostConstruct
	public void init() throws Exception {
		if (!configuration.getMfaDatabase().isEnabled()) {
			return;
		}

		// generate password derived secret key
		SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
		KeySpec spec = new PBEKeySpec(configuration.getMfaDatabase().getEncryptionKey().toCharArray(), new byte[] { 0x00, 0x01, 0x02, 0x03 }, 65536, 256);
		SecretKey tmp = factory.generateSecret(spec);
		encryptionKey = new SecretKeySpec(tmp.getEncoded(), "AES");
		
		// generate static IV
		byte[] ivData = new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
		iv = new IvParameterSpec(ivData);
	}

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

			if (mfaClients != null) {
				mfaClients = mfaClients.stream().filter(c -> configuration.getMfa().getEnabledClients().contains(c.getType().toString())).collect(Collectors.toList());
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
	
	public String encryptAndEncodeSsn(String ssn) throws Exception {
		if (ssn == null) {
			return null;
		}

		// remove slashes
		ssn = ssn.replace("-", "");
		
		// digest
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		byte[] ssnDigest = md.digest(ssn.getBytes(Charset.forName("UTF-8")));

		// encrypt
		byte[] encryptedDigestedSsn = encrypt(ssnDigest);

		// base64 encode
		return Base64.getEncoder().encodeToString(encryptedDigestedSsn);
	}

	private byte[] encrypt(byte[] data) throws Exception {
		Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
		cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, iv);
		
		return cipher.doFinal(data);
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
		catch (HttpClientErrorException ex) {
			if (HttpStatus.FORBIDDEN.equals(ex.getStatusCode())) {
				// This can happen if a users MFA client is locked parallel to a login-flow requiring MFA in the IdP
				log.warn("Failed initialise authentication, StatusCode Forbidden: " + ex);
			} else {
				log.error("Failed initialise authentication: " + ex);
			}
			return null;
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
		} catch (HttpClientErrorException ex) {
			if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
				log.warn("Failed to get mfa auth status for person with uuid " + person.getUuid() + ", and subscriptionKey = " + subscriptionKey, ex);
			} else {
				log.error("Failed to get mfa auth status for person with uuid " + person.getUuid() + ", and subscriptionKey = " + subscriptionKey, ex);
			}
			
			return false;
		} catch (Exception ex) {
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
	
	@Transactional
	public void synchronizeCachedMfaClients() {
		if (!configuration.getMfaDatabase().isEnabled()) {
			return;
		}
		
		StopWatch stopWatch = new StopWatch();
		stopWatch.start();
		
		log.info("Performing a synchronization of all MFA clients from OS2faktor datbase into cached clients table");
		
		// find all active persons and put into a cpr-based map
		Map<String, List<Person>> personMap = new HashMap<>();
		for (Person person : personService.getAll().stream().filter(p -> !p.isLocked()).collect(Collectors.toList())) {
			try {
				String encodedSsn = encryptAndEncodeSsn(person.getCpr());
				
				if (!personMap.containsKey(encodedSsn)) {
					personMap.put(encodedSsn, new ArrayList<>());
				}

				personMap.get(encodedSsn).add(person);
			}
			catch (Exception ex) {
				log.error("Unable to encode cpr for person " + person.getId(), ex);
			}
		}
		
		for (String encodedSsn : personMap.keySet()) {
			List<Person> persons = personMap.get(encodedSsn);
			
			maintainCachedClients(persons, lookupMfaClientsInDB(encodedSsn));
		}
		
		stopWatch.stop();
		log.info("completed in: " + stopWatch.toString());
	}

	private static final String selectClientsSql = "SELECT c.name, c.client_type, c.device_id, c.nsis_level FROM clients c JOIN users u ON u.id = c.user_id WHERE u.ssn = ? AND disabled = 0;";

	private List<MfaClient> lookupMfaClientsInDB(String encodedSsn) {
		return jdbcTemplate.query(
				selectClientsSql,
				(rs, rowNum) -> new MfaClient(rs.getString("name"), rs.getString("device_id"), rs.getString("client_type"), rs.getString("nsis_level")),
				encodedSsn);
	}

	private void maintainCachedClients(String cpr, List<MfaClient> mfaClients) {
		maintainCachedClients(personService.getByCpr(cpr), mfaClients);
	}	

	private void maintainCachedClients(List<Person> persons, List<MfaClient> mfaClients) {
		for (Person person : persons) {
			if (person.getMfaClients() == null) {
				// this case should never happen - Hibernate will make sure we get an empty collection
				List<CachedMfaClient> newCachedMFAClients = toCachedMFAClients(mfaClients, person);
				person.setMfaClients(newCachedMFAClients);

				personService.save(person);
			}
			else if (person.getMfaClients().size() == 0) {
				if (mfaClients != null && mfaClients.size() > 0) {
					person.getMfaClients().addAll(toCachedMFAClients(mfaClients, person));
	
					personService.save(person);
				}
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