package dk.digitalidentity.common.service;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.PersonDao;
import dk.digitalidentity.common.dao.model.PasswordChangeQueue;
import dk.digitalidentity.common.dao.model.PasswordSetting;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.ReplicationStatus;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.model.ADPasswordRequest;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PersonService {

	@Autowired
	private PersonDao personDao;

	@Autowired
	private AuditLogger auditLogger;

	@Autowired
	private PasswordSettingService passwordSettingService;

	@Autowired
	private CommonConfiguration configuration;

	@Autowired
	private PasswordChangeQueueService passwordChangeQueueService;


	public Person getById(long id) {
		return personDao.findById(id);
	}
	
	public List<Person> getByNemIdPid(String pid) {
		return personDao.findByNemIdPid(pid);
	}

	public Person save(Person entity) {
		return personDao.save(entity);
	}

	public void delete(Person person) {
		personDao.delete(person);
	}

	public List<Person> getAll() {
		return personDao.findAll();
	}

	public List<Person> getByCpr(String cpr) {
		return personDao.findByCpr(cpr);
	}

	public List<Person> getByUuid(String uuid) {
		return personDao.findByUuid(uuid);
	}

	public List<Person> getAllAdminsAndSupporters() {
		return personDao.findByAdminTrueOrSupporterTrue();
	}

	public List<Person> saveAll(List<Person> entities) {
		return personDao.saveAll(entities);
	}

	public List<Person> getBySamaccountName(String samAccountName) {
		return personDao.findBySamaccountName(samAccountName);
	}

	public List<Person> getByDomain(String domain) {
		return personDao.findByDomain(domain);
	}

	public List<Person> getBySamaccountNameAndDomain(String samAccountName, String domain) {
		return personDao.findBySamaccountNameAndDomain(samAccountName, domain);
	}

	public Person getByUserId(String userId) {
		return personDao.findByUserId(userId);
	}

	public void badPasswordAttempt(Person person) {
		auditLogger.badPassword(person);
		person.setBadPasswordCount(person.getBadPasswordCount() + 1);

		if (person.getBadPasswordCount() >= 5) {
			person.setLockedPassword(true);
			person.setLockedPasswordUntil(LocalDateTime.now().plusHours(1L));
		}

		save(person);
	}

	public void correctPasswordAttempt(Person person) {
		if (person.getBadPasswordCount() > 0) {
			person.setBadPasswordCount(0L);

			save(person);
		}
	}

	// TODO: auditlogning antager at det er brugeren selv der skifter kodeord (Det er det pt altid),
	//       men hvis vi på et tidspunkt vil understøtte password-skift fra administrators side,
	//       så bør vi håndtere auditlogning på en anden måde
	public void changePassword(Person person, String newPassword) throws NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, UnsupportedEncodingException {

		// Set new password and log
		if (person.hasNSISUser()) {
			BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
			person.setNsisPassword(encoder.encode(newPassword));
			auditLogger.changePasswordByPerson(person);
			save(person);
		}

		// Replicate password to AD if enabled
		PasswordSetting settings = passwordSettingService.getSettings();
		if (settings.isReplicateToAdEnabled()) {
			passwordChangeQueueService.createChange(person, newPassword);
		}
	}

	@Transactional(rollbackFor = Exception.class)
	public void unlockAccounts() {
		List<Person> all = personDao.findByLockedPasswordTrue();

		if (all != null && all.size() > 0) {
			List<Person> unlockedPersons = new ArrayList<>();

			for (Person person : all) {
				if (person.isLockedPassword() && person.getLockedPasswordUntil().isBefore(LocalDateTime.now())) {
					person.setBadPasswordCount(0);
					person.setLockedPassword(false);
					person.setLockedPasswordUntil(null);

					unlockedPersons.add(person);
				}
			}

			if (unlockedPersons.size() > 0) {
				saveAll(unlockedPersons);
			}
		}
	}

	@Transactional(rollbackFor = Exception.class)
	public void syncPasswordsToAD() {
		String url = configuration.getAd().getBaseUrl();
		if (!url.endsWith("/")) {
			url += "/";
		}
		url += "api/setPassword";

		// TODO: burde autowire denne, så vi kan konfigurere den i en @Configuration klasse
		RestTemplate restTemplate = new RestTemplate();

		for (PasswordChangeQueue change : passwordChangeQueueService.getUnsynchronized()) {
			try {
				ADPasswordRequest adPasswordRequest = new ADPasswordRequest();
				adPasswordRequest.setCpr(change.getCpr());
				adPasswordRequest.setSAMAccountName(change.getSamaccountName());
				adPasswordRequest.setPassword(passwordChangeQueueService.decryptPassword(change.getPassword());

				HttpHeaders headers = new HttpHeaders();
				headers.add("apiKey", configuration.getAd().getApiKey());
				HttpEntity<ADPasswordRequest> httpRequest = new HttpEntity<>(adPasswordRequest, headers);

				ResponseEntity<String> response = restTemplate.exchange(url , HttpMethod.POST, httpRequest, String.class);

				if (response.getStatusCodeValue() == 200) {
					change.setStatus(ReplicationStatus.SYNCHRONIZED);
				}
				else {
					change.setStatus(ReplicationStatus.ERROR);
					change.setMessage("Code: " + response.getStatusCode() + " Message: " + response.getBody());

					if (LocalDateTime.now().minusMinutes(10).isAfter(change.getTts())) {
						log.error("Replication failed, password change has not been replicated for more than 10 minutes (ID: " + change.getId() + ")");
					}
					else {
						log.warn("Password Replication failed, trying again in 1 minute (ID: " + change.getId() + ")");
					}
				}
				passwordChangeQueueService.save(change);
			}
			catch (Exception ex) {
				change.setStatus(ReplicationStatus.ERROR);
				change.setMessage("Failed to connect to AD Password replication service: " + ex.getMessage());
				passwordChangeQueueService.save(change);

				if (LocalDateTime.now().minusMinutes(10).isAfter(change.getTts())) {
					log.error("Replication failed, password change has not been replicated for more than 10 minutes (ID: " + change.getId() + ")");
				} else {
					log.warn("Password Replication failed, trying again in 1 minute (ID: " + change.getId() + ")");
				}
			}
		}
	}

	@Transactional(rollbackFor = Exception.class)
	public void syncQueueCleanupTask() {
		List<PasswordChangeQueue> synchronizedChanges = passwordChangeQueueService.getByStatus(ReplicationStatus.SYNCHRONIZED);

		for (PasswordChangeQueue synchronizedChange : synchronizedChanges) {
			LocalDateTime maxRetention = LocalDateTime.now().minusDays(7);
			if (synchronizedChange.getTts().isBefore(maxRetention)) {
				passwordChangeQueueService.delete(synchronizedChange);
			}
		}

	}
}