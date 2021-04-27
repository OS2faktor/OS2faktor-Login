package dk.digitalidentity.common.service;

import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.PasswordHistory;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.common.dao.PersonDao;
import dk.digitalidentity.common.dao.model.PasswordSetting;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.log.AuditLogger;
import org.springframework.util.StringUtils;

@Service
public class PersonService {

	@Autowired
	private PersonDao personDao;

	@Autowired
	private AuditLogger auditLogger;

	@Autowired
	private PasswordSettingService passwordSettingService;

	@Autowired
	private PasswordChangeQueueService passwordChangeQueueService;

	@Autowired
	private PasswordHistoryService passwordHistoryService;

	@Autowired
	private DomainService domainService;

	public Person getById(long id) {
		return personDao.findById(id);
	}
	
	public List<Person> getByNemIdPid(String pid) {
		return personDao.findByNemIdPid(pid);
	}

	public Person save(Person entity) {
		return personDao.save(entity);
	}

	public void delete(Person person, Person admin) {
		auditLogger.deletedUser(person, admin);
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
		return personDao.findByAdminTrueOrRegistrantTrueOrSupporterNotNull();
	}

	public List<Person> saveAll(List<Person> entities) {
		return personDao.saveAll(entities);
	}

	public List<Person> getBySamaccountName(String samAccountName) {
		return personDao.findBySamaccountName(samAccountName);
	}

	public List<Person> getByDomain(String domain) {
		Domain domainObj = domainService.getByName(domain);
		if (domainObj == null) {
			return null;
		}

		return personDao.findByDomain(domainObj);
	}

	public List<Person> getByDomain(Domain domain) {
		return personDao.findByDomain(domain);
	}

	public List<Person> getByDomainAndCpr(Domain domain, String cpr) {
		return personDao.findByDomainAndCpr(domain, cpr);
	}

	public List<Person> getBySamaccountNameAndDomain(String samAccountName, Domain domain) {
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
			person.setLockedPasswordUntil(LocalDateTime.now().plusMinutes(5L));
		}

		save(person);
	}

	public void correctPasswordAttempt(Person person) {
		if (person.getBadPasswordCount() > 0) {
			person.setBadPasswordCount(0L);

			save(person);
		}
	}

	public boolean changePassword(Person person, String newPassword) throws NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, UnsupportedEncodingException {
		return changePassword(person, newPassword, false, false);
	}

	/**
	 * Note that if bypass is set, then no replication is performed to Active Directory. This is because the
	 * common use-case for this, is an admin setting the users password, which should not be replicated to AD,
	 * as that could potentially lock out the user (also we do not want to expose AD passwords to the registrant)
	 */
	public boolean changePassword(Person person, String newPassword, boolean bypassValidation, boolean bypassReplication) throws NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, UnsupportedEncodingException {
		BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
		PasswordSetting settings = passwordSettingService.getSettings(person.getDomain());
		
		// Check for reuse of old passwords
		if (!bypassValidation) {
			if (settings.isDisallowOldPasswords()) {
				List<String> lastTenPasswords = passwordHistoryService.getLastTenPasswords(person);
				for (String oldPassword : lastTenPasswords) {
					if (encoder.matches(newPassword, oldPassword)) {
						return false;
					}
				}
			}
		}

		// Set new password and log
		if (person.hasNSISUser()) {
			String encodedPassword = encoder.encode(newPassword);
			person.setNsisPassword(encodedPassword);
			person.setNsisPasswordTimestamp(LocalDateTime.now());

			PasswordHistory passwordHistory = new PasswordHistory();
			passwordHistory.setPerson(person);
			passwordHistory.setPassword(encodedPassword);

			passwordHistoryService.save(passwordHistory);

			auditLogger.changePasswordByPerson(person);
			save(person);
		}
		
		// Replicate password to AD if enabled
		if (!bypassReplication) {
			if (settings.isReplicateToAdEnabled() && !StringUtils.isEmpty(person.getSamaccountName())) {
				passwordChangeQueueService.createChange(person, newPassword);
			}
		}

		return true;
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

	public static String maskCpr(String cpr) {
		if (cpr != null && cpr.length() > 6) {
			return cpr.substring(0, 6) + "-XXXX";
		}

		return "";
	}

	public void suspend(Person person) {
		person.setNsisLevel(NSISLevel.NONE);
		person.setApprovedConditions(false);
		person.setApprovedConditionsTts(null);
		person.setNsisPassword(null);
		person.setNsisPasswordTimestamp(null);
	}
}