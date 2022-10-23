package dk.digitalidentity.common.service;

import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.config.RoleSettingDTO;
import dk.digitalidentity.common.config.RoleSettingType;
import dk.digitalidentity.common.dao.PersonDao;
import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.Group;
import dk.digitalidentity.common.dao.model.PasswordChangeQueue;
import dk.digitalidentity.common.dao.model.PasswordHistory;
import dk.digitalidentity.common.dao.model.PasswordSetting;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.LogWatchSettingKey;
import dk.digitalidentity.common.dao.model.SchoolClass;
import dk.digitalidentity.common.dao.model.SchoolRole;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.dao.model.enums.ReplicationStatus;
import dk.digitalidentity.common.dao.model.enums.SchoolRoleValue;
import dk.digitalidentity.common.dao.model.mapping.PersonGroupMapping;
import dk.digitalidentity.common.dao.model.mapping.SchoolRoleSchoolClassMapping;
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
	
	@Autowired
	private LogWatchSettingService logWatchSettingService;
	
	@Autowired
	private EmailService emailService;

	@Qualifier("defaultTemplate")
	@Autowired
	private JdbcTemplate jdbcTemplate;
	
	@Autowired
	private CommonConfiguration commonConfiguration;

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

	@Transactional
	public List<Person> getBySamaccountNameAndDomainsFullyLoaded(String samAccountName, List<Domain> domains) {
		List<Person> persons = personDao.findBySamaccountNameAndDomainIn(samAccountName, domains);
		persons.forEach(p -> p.getGroups().forEach(PersonGroupMapping::loadFully));
		return persons;
	}

	public List<Person> getByDomainNotLockedByDataset(String domain, boolean searchChildren) {
		Domain domainObj = domainService.getByName(domain);
		if (domainObj == null) {
			return Collections.emptyList();
		}

		return getByDomainNotLockedByDataset(domainObj, searchChildren);
	}

	public List<Person> getByDomainNotLockedByDataset(Domain domain, boolean searchChildren) {
		ArrayList<Domain> toBeSearched = new ArrayList<>();
		toBeSearched.add(domain); // Always search the main domain

		if (searchChildren && domain.getChildDomains() != null && !domain.getChildDomains().isEmpty()) {
			List<Domain> childDomains = domain.getChildDomains();

			if (childDomains != null && !childDomains.isEmpty()) {
				toBeSearched.addAll(childDomains);
			}
		}

		return personDao.findByDomainInAndLockedDatasetFalse(toBeSearched);
	}

	public List<Person> getByDomain(String domain) {
		Domain domainObj = domainService.getByName(domain);
		if (domainObj == null) {
			return Collections.emptyList();
		}

		return getByDomain(domainObj, false);
	}

	public List<Person> getByDomain(String domain, boolean searchChildren) {
		return getByDomain(domain, searchChildren, false);
	}

	public List<Person> getByDomain(String domain, boolean searchChildren, boolean onlyNsisAllowed) {
		Domain domainObj = domainService.getByName(domain);
		if (domainObj == null) {
			return Collections.emptyList();
		}

		return getByDomain(domainObj, searchChildren, onlyNsisAllowed);
	}

	public List<Person> getByDomain(Domain domain, boolean searchChildren) {
		return getByDomain(domain, searchChildren, false);
	}
	
	public List<Person> getByDomain(Domain domain, boolean searchChildren, boolean onlyNsisAllowed) {
		ArrayList<Domain> toBeSearched = new ArrayList<>();
		toBeSearched.add(domain); // Always search the main domain

		if (searchChildren && domain.getChildDomains() != null && !domain.getChildDomains().isEmpty()) {
			List<Domain> childDomains = domain.getChildDomains();

			if (childDomains != null && !childDomains.isEmpty()) {
				toBeSearched.addAll(childDomains);
			}
		}

		if (onlyNsisAllowed) {
			return personDao.findByNsisAllowedTrueAndDomainIn(toBeSearched);
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
	
	public List<Person> getBySchoolRolesNotEmptyAndDomainIn(List<Domain> domains) {
		return personDao.findBySchoolRolesNotEmptyAndDomainIn(domains);
	}

	private List<Person> getStudentsByDomainAndInstititionIds(Domain domain, Set<String> institutionIds) {
		return personDao.findBySchoolRolesRoleAndDomainAndSchoolRolesInstitutionIdInAndNsisAllowedFalse(SchoolRoleValue.STUDENT, domain, institutionIds);
	}
	
	public boolean canChangePasswordOnStudents(Person person) {
		return (getSchoolRolesThatAllowChangingPassword(person).size() > 0);
	}

	private List<SchoolRole> getSchoolRolesThatAllowChangingPassword(Person person) {
		// safety checks
		if (!commonConfiguration.getStilStudent().isEnabled() || person == null) {
			return new ArrayList<>();
		}

		// filter out roles that are not allowed to change password
		List<SchoolRole> personAuthorityRoles = person.getSchoolRoles().stream().filter(r -> !r.getRole().equals(SchoolRoleValue.STUDENT)).collect(Collectors.toList());		
		for (Iterator<SchoolRole> iterator = personAuthorityRoles.iterator(); iterator.hasNext();) {
			SchoolRole schoolRole = iterator.next();

			for (RoleSettingDTO setting : commonConfiguration.getStilStudent().getRoleSettings()) {
				if (Objects.equals(setting.getRole(), schoolRole.getRole())) {
					if (setting.getType().equals(RoleSettingType.CANNOT_CHANGE_PASSWORD)) {
						iterator.remove();
					}
				}
			}
		}
		
		return personAuthorityRoles;
	}

	// this method does some heavy lifting, so only call from UI when listing students
	public List<Person> getStudentsThatPasswordCanBeChangedOnByPerson(Person person) {
		List<Person> result = new ArrayList<>();
		List<SchoolRole> personAuthorityRoles = getSchoolRolesThatAllowChangingPassword(person);

		// no roles that allows changing password - well, no go then
		if (personAuthorityRoles.isEmpty()) {
			return result;
		}

		// get institutions for smaller SQL lookup
		Set<String> institutionIds = new HashSet<>();
		for (SchoolRole schoolRole : personAuthorityRoles) {
			institutionIds.add(schoolRole.getInstitutionId());
		}

		// find all students from the same institutions that the teacher belongs to
		List<Person> students = getStudentsByDomainAndInstititionIds(person.getDomain(), institutionIds);

		for (Person student : students) {
			List<SchoolRole> studentRoles = student.getSchoolRoles().stream().filter(r -> r.getRole().equals(SchoolRoleValue.STUDENT)).collect(Collectors.toList());
			if (studentRoles.isEmpty()) { // not really needed, but safety first
				continue;
			}
			
			// iterate over all roles that the "teacher" has, to check if one allows access to this student
			boolean added = false;
			for (SchoolRole loggedInPersonSchoolRole : personAuthorityRoles) {
				
				// find the settings for this role
				RoleSettingDTO roleSetting = commonConfiguration.getStilStudent().getRoleSettings().stream()
						.filter(r -> r.getRole().equals(loggedInPersonSchoolRole.getRole()))
						.findFirst()
						.orElse(null);

				// redundant check, but safety first
				if (roleSetting == null || (roleSetting != null && roleSetting.getType().equals(RoleSettingType.CANNOT_CHANGE_PASSWORD))) {
					continue;
				}
				
				// the role allows changing password on students - now check if THIS student matches the criteria
				for (SchoolRole role : studentRoles) {

					// check for same institution, otherwise not relevant (cross-institution is never allowed)
					if (!role.getInstitutionId().equals(loggedInPersonSchoolRole.getInstitutionId())) {
						continue;
					}

					switch (roleSetting.getType()) {
						case CAN_CHANGE_PASSWORD_ON_GROUP_MATCH:
							List<String> filterClassTypes = Arrays.asList(roleSetting.getFilter().split(","));
							List<String> loggedInPersonSchoolRoleSchoolClassIds = loggedInPersonSchoolRole.getSchoolClasses().stream()
									.filter(c -> filterClassTypes.contains(c.getSchoolClass().getType().toString()))
									.map(c -> c.getSchoolClass().getClassIdentifier())
									.collect(Collectors.toList());

							for (SchoolRoleSchoolClassMapping schoolClassMapping : role.getSchoolClasses()) {
								SchoolClass schoolClass = schoolClassMapping.getSchoolClass();

								if (loggedInPersonSchoolRoleSchoolClassIds.contains(schoolClass.getClassIdentifier())) {
									result.add(student);
									added = true;
									break;
								}
							}

							break;
						case CAN_CHANGE_PASSWORD_ON_LEVEL_MATCH:
							List<String> filterClassLevels = Arrays.asList(roleSetting.getFilter().split(","));

							for (SchoolRoleSchoolClassMapping schoolClassMapping : role.getSchoolClasses()) {
								SchoolClass schoolClass = schoolClassMapping.getSchoolClass();
								
								if (schoolClass.getLevel() != null && filterClassLevels.contains(schoolClass.getLevel())) {
									result.add(student);
									added = true;
									break;
								}
							}

							break;
						case CANNOT_CHANGE_PASSWORD:
							break;
					}
					
					if (added) {
						break;
					}
				}
				
				if (added) {
					break;
				}
			}
		}
		
		return result;
	}

	public void badPasswordAttempt(Person person) {
		auditLogger.badPassword(person);
		person.setBadPasswordCount(person.getBadPasswordCount() + 1);
		PasswordSetting setting = passwordSettingService.getSettings(person.getDomain());

		if (person.getBadPasswordCount() >= setting.getTriesBeforeLockNumber()) {
			auditLogger.tooManyBadPasswordAttempts(person, setting.getLockedMinutes());
			person.setLockedPassword(true);
			person.setLockedPasswordUntil(LocalDateTime.now().plusMinutes(setting.getLockedMinutes()));
		}

		save(person);
	}

	public void correctPasswordAttempt(Person person, boolean authenticatedWithADPassword, boolean expired) {
		auditLogger.goodPassword(person, authenticatedWithADPassword, expired);
		if (person.getBadPasswordCount() > 0) {
			person.setBadPasswordCount(0L);

			save(person);
		}
	}

	public ADPasswordStatus changePassword(Person person, String newPassword, boolean bypassReplication) throws NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, UnsupportedEncodingException, InvalidAlgorithmParameterException {
		return changePassword(person, newPassword, bypassReplication, null);
	}

	/**
	 * Note that if bypass is set, then no replication is performed to Active Directory. This is because the
	 * common use-case for this, is an admin setting the users password, which should not be replicated to AD,
	 * as that could potentially lock out the user (also we do not want to expose AD passwords to the registrant)
	 */
	public ADPasswordStatus changePassword(Person person, String newPassword, boolean bypassReplication, Person admin) throws NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, UnsupportedEncodingException, InvalidAlgorithmParameterException {
		BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
		PasswordSetting settings = passwordSettingService.getSettings(person.getDomain());

		// sanity check if admin is available
		if (admin != null && !admin.isRegistrant() && person.isNsisAllowed()) {
			throw new RuntimeException("Kun registranter kan skifte kodeord på personer med en erhvervsidentitet!");
		}

		// Replicate password to AD if enabled
		boolean replicateToAD = false;
		ADPasswordStatus adPasswordStatus = ADPasswordStatus.NOOP;
		
		// if not flagged with bypass, attempt to change password in Active Directory
		if (!bypassReplication) {
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
			// this is usually the case if the password change originates from AD (using our WCP), in
			// which case we just log the password change as proof in our logs (consistency)

			PasswordChangeQueue change = new PasswordChangeQueue(person, passwordChangeQueueService.encryptPassword(newPassword));
			change.setStatus(ReplicationStatus.DO_NOT_REPLICATE);

			passwordChangeQueueService.save(change);
		}

		// make sure we have an encoded password from here on
		String encodedPassword = encoder.encode(newPassword);

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
	
	public static String getCorrectLockedPage(Person person) {
		if (person.isLockedExpired()) {
			return "error-expired-account";
		}

		return "error-locked-account";
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

	@Transactional(rollbackFor = Exception.class)
	public void handleExpired() {
		LocalDateTime now = LocalDateTime.now();
		List<Person> toBeExpiredPersons = personDao.findByLockedExpiredFalseAndExpireTimestampBefore(now);
		List<Person> modifiedPersons = new ArrayList<>();

		if (toBeExpiredPersons != null) {
			for (Person person : toBeExpiredPersons) {
				person.setLockedExpired(true);
				modifiedPersons.add(person);
			}
		}

		personDao.saveAll(modifiedPersons);
	}

	@Transactional
	public void logWatchTooManyLockedOnPassword() {
		long limit = logWatchSettingService.getLongWithDefault(LogWatchSettingKey.TOO_MANY_TIME_LOCKED_ACCOUNTS_LIMIT, 0);
		if (limit == 0) {
			return;
		}
		
		long logCount = personDao.countByLockedPasswordTrue();
		
		if (logCount > limit) {
			log.warn("Too many time locked accounts");
			
			String subject = "Overvågning af logs: For mange tids-spærrede konti";
			String message = "Antallet af tids-spærrede konti har oversteget grænsen på " + limit + ".<br/>Der er " + logCount + " tids-spærrede konti.";
			emailService.sendMessage(logWatchSettingService.getString(LogWatchSettingKey.ALARM_EMAIL), subject, message);
		}
	}
	
	public Set<String> findDistinctAttributeNames() {
		return personDao.findDistinctAttributeNames();
	}
}