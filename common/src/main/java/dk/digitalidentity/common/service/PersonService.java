package dk.digitalidentity.common.service;

import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import dk.digitalidentity.common.dao.PersonDao;
import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.Group;
import dk.digitalidentity.common.dao.model.PasswordChangeQueue;
import dk.digitalidentity.common.dao.model.PasswordHistory;
import dk.digitalidentity.common.dao.model.PasswordSetting;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.dao.model.enums.ReplicationStatus;
import dk.digitalidentity.common.dao.model.mapping.PersonGroupMapping;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.model.ADPasswordResponse.ADPasswordStatus;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PersonService {
	
	/*
	 * SELECT p.id
  		FROM persons p
  			INNER JOIN (
        			SELECT pa.id AS id, max(r.revtstmp) AS tts
        			FROM persons_aud pa
        			JOIN revinfo r ON r.id = pa.rev GROUP BY pa.id) rs
     			ON rs.id = p.id
  			WHERE p.locked_dataset = 1
    			AND rs.tts < ((UNIX_TIMESTAMP() - 60 * 60 * 24 * 30 * 13) * 1000);
	 */
	private static final String SELECT_PERSON_IDS = "SELECT p.id FROM persons p INNER JOIN (SELECT pa.id AS id, max(r.revtstmp) AS tts FROM persons_aud pa JOIN revinfo r ON r.id = pa.rev GROUP BY pa.id) rs ON rs.id = p.id WHERE p.locked_dataset = 1 AND rs.tts < ((UNIX_TIMESTAMP() - 60 * 60 * 24 * 30 * 13) * 1000);";

	/*
	 * DELETE FROM persons_aud
		WHERE id = ?;
	 */
	private static final String DELETE_FROM_AUD_BY_ID = "DELETE FROM persons_aud WHERE id = ?;";
	
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
	
	@Autowired
	private ADPasswordService adPasswordService;

	@Qualifier("defaultTemplate")
	@Autowired
	private JdbcTemplate jdbcTemplate;

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
		return personDao.findByAdminTrueOrServiceProviderAdminTrueOrRegistrantTrueOrSupporterNotNullOrUserAdminTrue();
	}

	public List<Person> saveAll(List<Person> entities) {
		return personDao.saveAll(entities);
	}

	public List<Person> getBySamaccountName(String samAccountName) {
		return personDao.findBySamaccountName(samAccountName);
	}

	@Transactional
	public List<Person> getBySamaccountNameFullyLoaded(String samAccountName) {
		List<Person> bySamaccountName = personDao.findBySamaccountName(samAccountName);
		bySamaccountName.forEach(p -> p.getGroups().forEach(PersonGroupMapping::loadFully));
		return bySamaccountName;
	}

	public List<Person> getByDomain(String domain) {
		Domain domainObj = domainService.getByName(domain);
		if (domainObj == null) {
			return Collections.emptyList();
		}

		return getByDomain(domainObj, false);
	}


	public List<Person> getByDomain(String domain, boolean searchChildren) {
		Domain domainObj = domainService.getByName(domain);
		if (domainObj == null) {
			return Collections.emptyList();
		}

		return getByDomain(domainObj, searchChildren);
	}

	public List<Person> getByDomain(Domain domain, boolean searchChildren) {
		ArrayList<Domain> toBeSearched = new ArrayList<>();
		toBeSearched.add(domain); // Always search the main domain

		if (searchChildren && domain.getChildDomains() != null && !domain.getChildDomains().isEmpty()) {
			List<Domain> childDomains = domain.getChildDomains();

			if (childDomains != null && !childDomains.isEmpty()) {
				toBeSearched.addAll(childDomains);
			}
		}

		return personDao.findByDomainIn(toBeSearched);
	}

	public List<Person> getByNotInGroup(Group group) {
		return personDao.findDistinctByGroupsGroupNotOrGroupsGroupNull(group);
	}

	public List<Person> getByDomainAndNotNSISAllowed(Domain domain) {
		return personDao.findByDomainAndNsisAllowed(domain, false);
	}

	public List<Person> getByDomainAndCpr(Domain domain, String cpr) {
		return getByDomainAndCpr(domain, cpr, false);
	}

	public List<Person> getByDomainAndCpr(Domain domain, String cpr, boolean searchChildren) {
		ArrayList<Domain> toBeSearched = new ArrayList<>();
		toBeSearched.add(domain);

		if (searchChildren && domain.getChildDomains() != null && !domain.getChildDomains().isEmpty()) {
			List<Domain> childDomains = domain.getChildDomains();

			if (childDomains != null && !childDomains.isEmpty()) {
				toBeSearched.addAll(childDomains);
			}
		}

		return personDao.findByCprAndDomainIn(cpr, toBeSearched);
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
		PasswordSetting setting = passwordSettingService.getSettings(person.getDomain());

		if (person.getBadPasswordCount() >= setting.getTriesBeforeLockNumber()) {
			auditLogger.tooManyBadPasswordAttempts(person);
			person.setLockedPassword(true);
			person.setLockedPasswordUntil(LocalDateTime.now().plusMinutes(setting.getLockedMinutes()));
		}

		save(person);
	}

	public void correctPasswordAttempt(Person person, boolean authenticatedWithADPassword) {
		auditLogger.goodPassword(person, authenticatedWithADPassword);
		if (person.getBadPasswordCount() > 0) {
			person.setBadPasswordCount(0L);

			save(person);
		}
	}

	public ADPasswordStatus changePassword(Person person, String newPassword, boolean bypassReplication) throws NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, UnsupportedEncodingException, InvalidAlgorithmParameterException {
		return changePassword(person, newPassword, bypassReplication, true, null);
	}

	/**
	 * Note that if bypass is set, then no replication is performed to Active Directory. This is because the
	 * common use-case for this, is an admin setting the users password, which should not be replicated to AD,
	 * as that could potentially lock out the user (also we do not want to expose AD passwords to the registrant)
	 */
	public ADPasswordStatus changePassword(Person person, String newPassword, boolean bypassReplication, boolean shouldEncodePassword, Person admin) throws NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, UnsupportedEncodingException, InvalidAlgorithmParameterException {
		BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
		PasswordSetting settings = passwordSettingService.getSettings(person.getDomain());

		// sanity check if admin is available
		if (admin != null && !admin.isRegistrant() && person.isNsisAllowed()) {
			throw new RuntimeException("Kun registranter kan skifte kodeord på personer med en erhvervsidentitet!");
		}

		// Replicate password to AD if enabled
		boolean replicateToAD = false;
		ADPasswordStatus adPasswordStatus = ADPasswordStatus.NOOP;
		
		// we want to avoid replicating pre-encoded passwords (just an extra safety check, to protect against lazy programmers ;))
		// we can't replicate passwords that are already encoded,
		// so in addition to bypassReplication we check if we are asked to encode the password while changing it as a sanity check
		if (!bypassReplication && shouldEncodePassword) {
			if (settings.isReplicateToAdEnabled() && StringUtils.hasLength(person.getSamaccountName())) {
				adPasswordStatus = passwordChangeQueueService.attemptPasswordChange(person, newPassword);

				switch (adPasswordStatus) {
		            case FAILURE:
		            case TECHNICAL_ERROR:
		            	// this should be handled by the caller - either bypass replication, or fail
		            	return adPasswordStatus;
		            case NOOP:
		            case OK:
		            case TIMEOUT:
		            	// these are OK
		            	break;
	            }

				replicateToAD = true;
			}
		}
		else {
			PasswordChangeQueue change = new PasswordChangeQueue();
			change.setStatus(ReplicationStatus.DO_NOT_REPLICATE);
			change.setDomain(person.getDomain().getName());
			change.setSamaccountName(person.getSamaccountName());
			change.setUuid(person.getUuid());
			change.setPassword("N/A");

			passwordChangeQueueService.save(change);
		}

		// make sure we have an encoded password from here on
		String encodedPassword = newPassword;
		if (shouldEncodePassword) {
			encodedPassword = encoder.encode(newPassword);
		}

		// update password counter for this person
		person.setDailyPasswordChangeCounter(person.getDailyPasswordChangeCounter() + 1);

		// store password history
		PasswordHistory passwordHistory = new PasswordHistory();
		passwordHistory.setPerson(person);
		passwordHistory.setPassword(encodedPassword);
		passwordHistoryService.save(passwordHistory);

		// set new password locally if NSIS is enabled for this user
		boolean nsisPasswordChanged = false;
		if (person.isNsisAllowed()) {
			person.setNsisPassword(encodedPassword);
			person.setNsisPasswordTimestamp(LocalDateTime.now());
			person.setForceChangePassword(false);

			nsisPasswordChanged = true;

			save(person);
		}

		// auditlog it
		if (admin == null) {
			auditLogger.changePasswordByPerson(person, nsisPasswordChanged, replicateToAD);
		}
		else {
			auditLogger.changePasswordByAdmin(admin, person, replicateToAD);
		}

		return adPasswordStatus;
	}
	
	public ADPasswordStatus unlockADAccount(Person person) {
		ADPasswordStatus adPasswordStatus = ADPasswordStatus.NOOP;
		if (StringUtils.hasLength(person.getSamaccountName())) {
			adPasswordStatus = adPasswordService.attemptUnlockAccount(person);
			auditLogger.unlockAccountByPerson(person);
		}

		return adPasswordStatus;
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

	public static String getUsername(Person person) {
		if (person == null) {
			return null;
		}
		else if (StringUtils.hasLength(person.getSamaccountName())) {
			return person.getSamaccountName();
		}

		return person.getUserId();
	}

	@Transactional(rollbackFor = Exception.class)
	public void cleanUp() {

		@SuppressWarnings("deprecation")
		List<Long> idsToBeDeleted = jdbcTemplate.query(SELECT_PERSON_IDS, new Object[] {}, (RowMapper<Long>) (rs, rownum) -> {
			return rs.getLong("id");
		});
		
		for (Long id : idsToBeDeleted) {
		    Object[] args = new Object[] {id};
		    int rows = jdbcTemplate.update(DELETE_FROM_AUD_BY_ID, args);

		    log.info("Deleted " + rows + " aud row for id = " + id);
		}
		
		List<Person> personsToBeDeleted = personDao.findAll().stream().filter(p -> idsToBeDeleted.contains(p.getId())).collect(Collectors.toList());
		personDao.deleteAll(personsToBeDeleted);
		
		log.info("Deleted " + personsToBeDeleted.size() + " persons because they where removed from dataset more than 13 months ago");
	}
	
	@Transactional(rollbackFor = Exception.class)
	public void resetDailyPasswordCounter() {
		personDao.resetDailyPasswordChangeCounter();
	}
}