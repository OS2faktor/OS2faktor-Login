package dk.digitalidentity.common.service.mfa;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.spec.KeySpec;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.MfaLoginHistoryDao;
import dk.digitalidentity.common.dao.model.CachedMfaClient;
import dk.digitalidentity.common.dao.model.LocalRegisteredMfaClient;
import dk.digitalidentity.common.dao.model.MfaLoginHistory;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.service.LocalRegisteredMfaClientService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.dto.MfaAuthenticationResponseDTO;
import dk.digitalidentity.common.service.mfa.model.ClientType;
import dk.digitalidentity.common.service.mfa.model.MFAClientDetails;
import dk.digitalidentity.common.service.mfa.model.MfaAuthenticationResponse;
import dk.digitalidentity.common.service.mfa.model.MfaClient;
import dk.digitalidentity.common.service.mfa.model.ProjectionClient;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
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

	@Autowired
	private CommonConfiguration configuration;
	
	@Autowired
	private LocalRegisteredMfaClientService localRegisteredMfaClientService;
	
	@Autowired
	private PersonService personService;
	
	@Autowired
	private MFAManagementService mfaManagementService;
	
	@Autowired
	private MfaLoginHistoryDao mfaLoginHistoryDao;

	@Autowired
	private MFAService self;
	
	@PostConstruct
	public void init() throws Exception {
		if (configuration.getMfaDatabase().getEncryptionKey() != null) {
			// generate password derived secret key
			SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
			KeySpec spec = new PBEKeySpec(configuration.getMfaDatabase().getEncryptionKey().toCharArray(), new byte[] { 0x00, 0x01, 0x02, 0x03 }, 65536, 256);
			SecretKey tmp = factory.generateSecret(spec);
			encryptionKey = new SecretKeySpec(tmp.getEncoded(), "AES");
			
			// generate static IV
			byte[] ivData = new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
			iv = new IvParameterSpec(ivData);
		}
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
			// we don't really care, except for debugging purposes
			log.warn("Failed to get details for client: " + deviceId + " / " + ex.getMessage());
		}

		return null;
	}

	public List<MfaClient> getClients(String cpr) {
		return getClients(cpr, false);
	}

	public boolean hasPasswordlessMfa(Person person) {
		// if disabled, the person will never "have" any such MFA clients
		if (!configuration.getCustomer().isEnablePasswordlessMfa()) {
			return false;
		}

		// this is a simply precheck to optimize loginflow - the first login with a freshly registered passwordless MFA
		// will not trigger the flow, but we avoid having to lookup MFA clients every single login for other users
		boolean cachedHit = person.getMfaClients().stream().anyMatch(m -> m.isPasswordless());
		if (!cachedHit) {
			return false;
		}
		
		// TODO: can we cache this?
		List<MfaClient> clients = getClients(person.getCpr(), person.isRobot());
		
		return clients.stream().anyMatch(m -> m.isPasswordless());
	}

	public List<MfaClient> getClients(String cpr, boolean isRobot) {
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
					// local clients might still be locked, so lookup details on backup
					MFAClientDetails mfaClientDetails = getClientDetails(localClient.getDeviceId());
					if (mfaClientDetails == null) {
						continue;
					}
					
					MfaClient client = new MfaClient();
					client.setDeviceId(localClient.getDeviceId());
					client.setName(localClient.getName());
					client.setNsisLevel(localClient.getNsisLevel());
					client.setType(localClient.getType());
					client.setLocalClient(true);
					client.setPrime(false);
					client.setLocked(false);

					client.setLocked(mfaClientDetails.isLocked());
					if (client.isLocked()) {
						client.setLockedUntil(mfaClientDetails.getLockedUntil());
					}

					if (localClient.isPrime()) {
						client.setPrime(true);
						mfaClients.forEach(c -> c.setPrime(false));
					}
					
					mfaClients.add(client);
				}
			}

			if (mfaClients != null) {
				mfaClients = mfaClients.stream()
						// robots are not filtered
						.filter(c -> isRobot || configuration.getMfa().getEnabledClients().contains(c.getType().toString()))
						.collect(Collectors.toList());
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
			log.error("Failed to get mfa client", ex);
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
				// TODO: emitChallenge=false undertrykker kontrolkoden, men nyeste logik i app'en undertrykker visning af 2 ens kontrolkoder. Det skal den så ikke hvis dette
				// skal virke. Så app'en skal tillade kontrolkoder der er blanke altid, og kun undertrykke dubletter med faktiske kontrolkoder. Dette skal være kommenteret
				// ud til ændringen er lavet i app'en
				String url = configuration.getMfa().getBaseUrl() + "/api/server/client/" + client.getDeviceId() + "/authenticate"; // ?emitChallenge=false";

				ResponseEntity<MfaAuthenticationResponse> result = restTemplate.exchange(url, HttpMethod.PUT, entity, new ParameterizedTypeReference<MfaAuthenticationResponse>() { });
				response.add(result.getBody());
			}
			catch (HttpClientErrorException ex) {
				if (HttpStatus.GONE.equals(ex.getStatusCode())) {
					// The access to the client has been removed, this is due to the client being disabled/reset
					// If we have this client stored as a local client it should be removed before continuing
					LocalRegisteredMfaClient localClient = localRegisteredMfaClientService.getByDeviceId(client.getDeviceId());
					if (localClient != null) {
						localRegisteredMfaClientService.delete(localClient);
						log.warn("Failed initialise authentication with deviceId " + client.getDeviceId() + ", Device disabled in OS2faktor. Deleting matching local client");
					}
					else {
						log.warn("Failed initialise authentication with deviceId " + client.getDeviceId() + ", Device disabled in OS2faktor");
					}
				}
				else if (HttpStatus.FORBIDDEN.equals(ex.getStatusCode())) {
					// This can happen if a users MFA client is locked parallel to a login-flow requiring MFA in the IdP
					log.warn("Failed initialise authentication with cpr, StatusCode Forbidden");
				}
				else if (HttpStatus.NOT_FOUND.equals(ex.getStatusCode())) {
					// This can happen if a users MFA client is locked parallel to a login-flow requiring MFA in the IdP
					log.warn("Failed initialise authentication with cpr, StatusCode NotFound");
				}
				else {
					log.error("Failed initialise authentication with cpr", ex);
				}
			}
			catch (Exception ex) {
				log.error("Failed initialise authentication with cpr", ex);
			}			
		}
		
		return response;
	}

	public MfaAuthenticationResponseDTO authenticate(String deviceId, boolean passwordless) {
		HttpHeaders headers = new HttpHeaders();
		headers.add("ApiKey", configuration.getMfa().getApiKey());
		headers.add("connectorVersion", connectorVersion);
		HttpEntity<String> entity = new HttpEntity<>(headers);
		MfaAuthenticationResponseDTO dto = new MfaAuthenticationResponseDTO();

		try {
			String url = configuration.getMfa().getBaseUrl() + "/api/server/client/" + deviceId + "/authenticate";
			if (passwordless) {
				url = url + "?passwordless=true";
			}

			ResponseEntity<MfaAuthenticationResponse> response = restTemplate.exchange(url, HttpMethod.PUT, entity, new ParameterizedTypeReference<MfaAuthenticationResponse>() { });
			dto.setMfaAuthenticationResponse(response.getBody());
			dto.setSuccess(true);
		}
		catch (HttpClientErrorException ex) {
			dto.setSuccess(false);
			dto.setFailureMessage(ex.getMessage());

			if (HttpStatus.GONE.equals(ex.getStatusCode())) {
				// The access to the client has been removed, this is due to the client being disabled/reset
				// If we have this client stored as a local client it should be removed before continuing
				LocalRegisteredMfaClient localClient = localRegisteredMfaClientService.getByDeviceId(deviceId);
				if (localClient != null) {
					localRegisteredMfaClientService.delete(localClient);
					log.warn("Failed initialise authentication with deviceId " + deviceId + ", Device disabled in OS2faktor. Deleting matching local client");
				}
				else {
					log.warn("Failed initialise authentication with deviceId " + deviceId + ", Device disabled in OS2faktor");
				}
			}
			else if (HttpStatus.FORBIDDEN.equals(ex.getStatusCode())) {
				// This can happen if a users MFA client is locked parallel to a login-flow requiring MFA in the IdP
				log.warn("Failed initialise authentication with deviceId " + deviceId + ", StatusCode Forbidden");
			}
			else if (HttpStatus.NOT_FOUND.equals(ex.getStatusCode())) {
				// Not Found can happen if a device does not exist in MFA or if a phone app is disabled in the AWS notification system
				// The access to the client has been removed, if we have this client stored as a local client it should be removed before continuing
				LocalRegisteredMfaClient localClient = localRegisteredMfaClientService.getByDeviceId(deviceId);
				if (localClient != null) {
					localRegisteredMfaClientService.delete(localClient);
					log.warn("Failed initialise authentication with deviceId " + deviceId + ", Device not found in OS2faktor. Deleting matching local client");
				}
				else {
					log.warn("Failed initialise authentication with deviceId " + deviceId + ", StatusCode NotFound");
				}
			}
			else {
				log.error("Failed initialise authentication with deviceId " + deviceId, ex);
			}
		}
		catch (Exception ex) {
			log.error("Failed initialise authentication with deviceId " + deviceId, ex);
		}
		
		return dto;
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
			}
			else {
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
			log.error("Failed to get mfa auth status for person with uuid " + person.getUuid(), ex);
			return null;
		}
	}
	
	public void synchronizeCachedMfaClients() {
		StopWatch stopWatch = new StopWatch();
		stopWatch.start();
		
		log.info("Performing a synchronization of all MFA clients from OS2faktor database into cached clients table");
		
		// find all active persons and put into a cpr-based map
		Map<String, List<Person>> personMap = new HashMap<>();
		for (Person person : personService.getAll(p -> p.getMfaClients().size()).stream().collect(Collectors.toList())) {
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
		
		List<ProjectionClient> allLocallyRegisteredMfaClients = mfaManagementService.lookupLocalMfaClientsInCentral(personMap);
		Map<String, List<ProjectionClient>> allLocallyRegisteredMfaClientsBySsn = allLocallyRegisteredMfaClients.stream().collect(Collectors.groupingBy(ProjectionClient::getSsn));
		
		List<ProjectionClient> allRegisteredMfaClients = mfaManagementService.lookupMfaClientsInCentral(personMap);
		Map<String, List<ProjectionClient>> allRegisteredMfaClientsBySsn = allRegisteredMfaClients.stream().collect(Collectors.groupingBy(ProjectionClient::getSsn));

		List<LocalRegisteredMfaClient> locallyRegisteredClients = localRegisteredMfaClientService.getAll();
		Map<String, List<LocalRegisteredMfaClient>> locallyRegisteredClientsBySsn = locallyRegisteredClients.stream().collect(Collectors.groupingBy(LocalRegisteredMfaClient::getCpr));

		for (String encodedSsn : personMap.keySet()) {
			List<Person> persons = personMap.get(encodedSsn);
			
			List<MfaClient> mfaClients = new ArrayList<>();
			Set<String> mfaClientsDeviceIds = new HashSet<>();
			
			// globally stored in OS2faktor MFA
			List<ProjectionClient> storedMfaClients = allRegisteredMfaClientsBySsn.get(encodedSsn);
			if (storedMfaClients != null) {
				for (ProjectionClient localClient : storedMfaClients) {
					if (!mfaClientsDeviceIds.contains(localClient.getDeviceId())) {
						MfaClient client = new MfaClient();
						client.setDeviceId(localClient.getDeviceId());
						client.setName(localClient.getName());
						client.setPasswordless(localClient.isPasswordless());
						client.setLocalClient(true);
						client.setSerialnumber(localClient.getSerialnumber());
						client.setLastUsed(localClient.getLastUsed());
						client.setAssociatedUserTimestamp(localClient.getAssociatedUserTimestamp());

						try {
							client.setType(ClientType.valueOf(localClient.getClientType()));
							client.setNsisLevel(NSISLevel.valueOf(localClient.getNsisLevel()));
						}
						catch (Exception ex) {
							log.warn("Unable to parse enum value for " + localClient.getDeviceId(), ex);
							continue;
						}

						mfaClients.add(client);
						mfaClientsDeviceIds.add(localClient.getDeviceId());
					}
				}
			}

			// locally stored in OS2faktor MFA
			List<ProjectionClient> locallyStoredMfaClients = allLocallyRegisteredMfaClientsBySsn.get(encodedSsn);
			if (locallyStoredMfaClients != null) {
				for (ProjectionClient localClient : locallyStoredMfaClients) {
					if (!mfaClientsDeviceIds.contains(localClient.getDeviceId())) {
						MfaClient client = new MfaClient();
						client.setDeviceId(localClient.getDeviceId());
						client.setName(localClient.getName());
						client.setPasswordless(localClient.isPasswordless());
						client.setLocalClient(true);
						client.setSerialnumber(localClient.getSerialnumber());
						client.setLastUsed(localClient.getLastUsed());
						client.setAssociatedUserTimestamp(localClient.getAssociatedUserTimestamp());

						try {
							client.setType(ClientType.valueOf(localClient.getClientType()));
							client.setNsisLevel(NSISLevel.valueOf(localClient.getNsisLevel()));
						}
						catch (Exception ex) {
							log.warn("Unable to parse enum value for " + localClient.getDeviceId(), ex);
							continue;
						}

						mfaClients.add(client);
						mfaClientsDeviceIds.add(localClient.getDeviceId());
					}
				}
			}

			// locally stored in OS2faktor Login
			List<LocalRegisteredMfaClient> localClients = locallyRegisteredClientsBySsn.get(persons.get(0).getCpr());
			if (localClients != null) {
				for (LocalRegisteredMfaClient localClient : localClients) {
					if (!mfaClientsDeviceIds.contains(localClient.getDeviceId())) {
						MfaClient client = new MfaClient();
						client.setDeviceId(localClient.getDeviceId());
						client.setName(localClient.getName());
						client.setNsisLevel(localClient.getNsisLevel());
						client.setType(localClient.getType());
						client.setLocalClient(true);

						if (localClient.getAssociatedUserTimestamp() != null) {
							client.setAssociatedUserTimestamp(Instant.ofEpochMilli(localClient.getAssociatedUserTimestamp().getTime())
									.atZone(ZoneId.systemDefault())
									.toLocalDateTime());
						}

						mfaClients.add(client);
						mfaClientsDeviceIds.add(localClient.getDeviceId());
					}
				}
			}

			self.maintainCachedClients(persons, mfaClients);
		}
		
		stopWatch.stop();
		log.info("completed in: " + stopWatch.toString());
	}

	private void maintainCachedClients(String cpr, List<MfaClient> mfaClients) {
		maintainCachedClients(personService.getByCpr(cpr, p -> p.getMfaClients().size()), mfaClients);
	}	

	@Transactional // this is as OK as I can make it in a fast and easy way - optimize later if needed
	public void maintainCachedClients(List<Person> persons, List<MfaClient> mfaClients) {
		try {
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
	
							if (!Objects.equals(mfaClient.getName(), cachedClientToUpdate.getName())) {
								changes = true;
								cachedClientToUpdate.setName(mfaClient.getName());
							}
	
							if (mfaClient.isPasswordless() != cachedClientToUpdate.isPasswordless()) {
								changes = true;
								cachedClientToUpdate.setPasswordless(mfaClient.isPasswordless());
							}
	
							if (!Objects.equals(mfaClient.getSerialnumber(), cachedClientToUpdate.getSerialnumber())) {
								changes = true;
								cachedClientToUpdate.setSerialnumber(mfaClient.getSerialnumber());
							}
	
							// NSISLevel cannot currently change in OS2faktor MFA, but let's support it here
							if (!Objects.equals(mfaClient.getNsisLevel(), cachedClientToUpdate.getNsisLevel())) {
								changes = true;
								cachedClientToUpdate.setNsisLevel(mfaClient.getNsisLevel());
							}
	
							if (!Objects.equals(mfaClient.getLastUsed(), cachedClientToUpdate.getLastUsed())) {
								changes = true;
								cachedClientToUpdate.setLastUsed(mfaClient.getLastUsed());
							}
	
							if (!Objects.equals(mfaClient.getAssociatedUserTimestamp(), cachedClientToUpdate.getAssociatedUserTimestamp())) {
								changes = true;
								cachedClientToUpdate.setAssociatedUserTimestamp(mfaClient.getAssociatedUserTimestamp());
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
		catch (OptimisticLockingFailureException ex) {
			log.warn("Failed to persist changes to CachedMfaClients due to OptimisticLockingFailureException: " + ex.getMessage());
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
			cachedClient.setSerialnumber(client.getSerialnumber());
			cachedClient.setLastUsed(client.getLastUsed());
			cachedClient.setPasswordless(client.isPasswordless());
			cachedClient.setAssociatedUserTimestamp(client.getAssociatedUserTimestamp());

			newCachedMFAClients.add(cachedClient);
		}
		
		return newCachedMFAClients;
	}

	@Transactional
	public void removeOldMfaLoginHistory() {
		mfaLoginHistoryDao.deleteOld();
	}

	public void fetchMfaLoginHistory() {
		LocalDateTime maxCreatedTts = mfaLoginHistoryDao.getMaxCreatedTts();
		if (maxCreatedTts == null) {
			maxCreatedTts = LocalDateTime.now().minusHours(1);
		}
		
		// skip one second, as query includes current timestamp
		maxCreatedTts = maxCreatedTts.plusSeconds(1);

		List<MfaLoginHistory> history = mfaManagementService.fetchMfaLoginHistory(maxCreatedTts);
		if (history != null && history.size() > 0) {
			mfaLoginHistoryDao.saveAll(history);
		}
	}
}