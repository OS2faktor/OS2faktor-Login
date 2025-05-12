package dk.digitalidentity.common.service;

import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.config.modules.school.StudentPwdRoleSettingConfiguration;
import dk.digitalidentity.common.dao.PersonDao;
import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.Group;
import dk.digitalidentity.common.dao.model.NemloginQueue;
import dk.digitalidentity.common.dao.model.PasswordChangeQueue;
import dk.digitalidentity.common.dao.model.PasswordHistory;
import dk.digitalidentity.common.dao.model.PasswordSetting;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.SchoolClass;
import dk.digitalidentity.common.dao.model.SchoolRole;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.dao.model.enums.NemloginAction;
import dk.digitalidentity.common.dao.model.enums.ReplicationStatus;
import dk.digitalidentity.common.dao.model.enums.RoleSettingType;
import dk.digitalidentity.common.dao.model.enums.SchoolRoleValue;
import dk.digitalidentity.common.dao.model.mapping.PersonGroupMapping;
import dk.digitalidentity.common.dao.model.mapping.SchoolRoleSchoolClassMapping;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.dto.ChildDTO;
import dk.digitalidentity.common.service.dto.CprLookupDTO;
import dk.digitalidentity.common.service.model.ADPasswordResponse;
import dk.digitalidentity.common.service.model.ADPasswordResponse.ADPasswordStatus;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@EnableCaching
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
	private TermsAndConditionsService termsAndConditionsService;

	@Qualifier("defaultTemplate")
	@Autowired
	private JdbcTemplate jdbcTemplate;
	
	@Autowired
	private CommonConfiguration commonConfiguration;
	
	@Autowired 
	private SchoolClassService schoolClassService;
	
	@Autowired
	private NemloginQueueService nemloginQueueService;
	
	@Autowired
	private CprService cprService;

	public Person getById(long id) {
		return personDao.findById(id);
	}
	
	public List<Person> getByNemIdPid(String pid) {
		return personDao.findByNemIdPid(pid);
	}

	public Person save(Person entity) {
		return personDao.save(entity);
	}

	public List<Person> getByTransferToNemLogin() {
		return personDao.findByTransferToNemloginTrue();
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
	
	public List<Person> getByMitIdNameId(String mitIdNameId) {
		return personDao.findByMitIdNameId(mitIdNameId);
	}

	public List<Person> getByUuid(String uuid) {
		return personDao.findByUuid(uuid);
	}
	
	public List<Person> getByNemloginUserUuidNotNull() {
		return personDao.findByNemloginUserUuidNotNull();
	}

	public List<Person> getAllAdminsAndSupporters() {
		return personDao.findByAdminTrueOrServiceProviderAdminTrueOrRegistrantTrueOrSupporterNotNullOrUserAdminTrueOrKodeviserAdminTrueOrInstitutionStudentPasswordAdminTrue();
	}

	public List<Person> saveAll(List<Person> persons) {
		return personDao.saveAll(persons);
	}

	public List<Person> getBySamaccountName(String samAccountName) {
		return personDao.findBySamaccountName(samAccountName);
	}

	public List<Person> getByUPN(String username) {
		return personDao.findByAttributeValue("upn", username);
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
	
	public List<Person> getBySamaccountNameAndDomains(String samAccountName, List<Domain> domains) {
		return personDao.findBySamaccountNameAndDomainIn(samAccountName, domains);
	}
	
	public List<Person> getBySchoolRolesNotEmptyAndDomainIn(List<Domain> domains) {
		return personDao.findBySchoolRolesNotEmptyAndDomainIn(domains);
	}

	// always call with top-level-domain
	private List<Person> getStudentsByDomainAndInstititionIds(Domain domain, Set<String> institutionIds) {
		List<Person> result = new ArrayList<>();
		
		// get from top level domain, and then look in subdomains afterwards
		result.addAll(personDao.findBySchoolRolesRoleAndDomainAndSchoolRolesInstitutionIdInAndNsisAllowedFalse(SchoolRoleValue.STUDENT, domain, institutionIds));

		// then look in all subdomains
		for (Domain childDomain : domain.getChildDomains()) {
			result.addAll(personDao.findBySchoolRolesRoleAndDomainAndSchoolRolesInstitutionIdInAndNsisAllowedFalse(SchoolRoleValue.STUDENT, childDomain, institutionIds));			
		}
		
		return result;
	}

	private List<Person> getAllStudentsWithStudentPassword() {
		return personDao.findBySchoolRolesRoleAndStudentPasswordNotNull(SchoolRoleValue.STUDENT);
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
		if (!person.isInstitutionStudentPasswordAdmin()) {
			for (Iterator<SchoolRole> iterator = personAuthorityRoles.iterator(); iterator.hasNext();) {
				SchoolRole schoolRole = iterator.next();

				for (StudentPwdRoleSettingConfiguration setting : commonConfiguration.getStilStudent().getRoleSettings()) {
					if (Objects.equals(setting.getRole(), schoolRole.getRole())) {
						if (setting.getType().equals(RoleSettingType.CANNOT_CHANGE_PASSWORD)) {
							iterator.remove();
						}
					}
				}
			}
		}

		return personAuthorityRoles;
	}

	// this method does some heavy lifting, so only call from UI when listing students (optionalClassFilter can be null)
	public List<Person> getStudentsThatPasswordCanBeChangedOnByPerson(Person person, SchoolClass optionalClassFilter) {
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
		List<Person> students = getStudentsByDomainAndInstititionIds(person.getTopLevelDomain(), institutionIds);

		for (Person student : students) {
			if (student.isLockedDataset() || !StringUtils.hasLength(student.getSamaccountName())) {
				continue;
			}

			List<SchoolRole> studentRoles = student.getSchoolRoles().stream().filter(r -> r.getRole().equals(SchoolRoleValue.STUDENT)).collect(Collectors.toList());
			if (studentRoles.isEmpty()) { // not really needed, but safety first
				continue;
			}

			// if the "teacher" isInstitutionStudentPasswordAdmin password change is allowed on all students inside its own institutions
			if (person.isInstitutionStudentPasswordAdmin()) {
				// we still have to respect any optional classFilter though
				if (optionalClassFilter != null) {
					boolean match = false;

					for (SchoolRole role : studentRoles) {
						if (!role.getSchoolClasses().stream().anyMatch(r -> r.getSchoolClass().getId() == optionalClassFilter.getId())) {
							continue;
						}
						
						match = true;
					}
					
					if (!match) {
						continue;
					}
				}

				result.add(student);
				continue;
			}
			
			// iterate over all roles that the "teacher" has, to check if one allows access to this student
			boolean added = false;
			for (SchoolRole loggedInPersonSchoolRole : personAuthorityRoles) {
				
				// find the settings for this role
				StudentPwdRoleSettingConfiguration roleSetting = commonConfiguration.getStilStudent().getRoleSettings().stream()
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
					
					// if an optional filter is supplied, ignore any studentRole that does not point to the class we are filtering on
					if (optionalClassFilter != null) {
						if (!role.getSchoolClasses().stream().anyMatch(r -> r.getSchoolClass().getId() == optionalClassFilter.getId())) {
							continue;
						}
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

	public void badPasswordAttempt(Person person, boolean isWCP) {
		auditLogger.badPassword(person, isWCP);
		person.setBadPasswordCount(person.getBadPasswordCount() + 1);
		PasswordSetting setting = passwordSettingService.getSettings(person);

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

	public ADPasswordStatus changePassword(Person person, String newPassword) throws NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, UnsupportedEncodingException, InvalidAlgorithmParameterException {
		return changePassword(person, newPassword, false, null, null, false);
	}

	public ADPasswordStatus changePasswordBypassQueue(Person person, String newPassword) throws NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, UnsupportedEncodingException, InvalidAlgorithmParameterException {
		return changePassword(person, newPassword, true, null, null, false);
	}

	public ADPasswordStatus changePasswordByParent(Person person, String newPassword, String parentCpr) throws NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, UnsupportedEncodingException, InvalidAlgorithmParameterException {
		return changePassword(person, newPassword, false, null, parentCpr, false);
	}

	public ADPasswordStatus changePasswordByAdmin(Person person, String newPassword, Person admin, boolean forceChangePassword) throws NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, UnsupportedEncodingException, InvalidAlgorithmParameterException {
		return changePassword(person, newPassword, false, admin, null, forceChangePassword);
	}

	/**
	 * Note that if bypass is set, then no replication is performed to Active Directory. This is because the
	 * common use-case for this, is an admin setting the users password, which should not be replicated to AD,
	 * as that could potentially lock out the user (also we do not want to expose AD passwords to the registrant)
	 */
	public ADPasswordStatus changePassword(Person person, String newPassword, boolean bypassReplication, Person admin, String parentCpr, boolean forceChangePassword) throws NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, UnsupportedEncodingException, InvalidAlgorithmParameterException {
		BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

		// sanity check if admin is available with required role for changing password on NSIS users
		// note that calls from IdentitiesController still need to perform verification that the user has the Kodeordsadministrator role,
		// and that it only should allow changing password on non-nsis users - this check is for registrant-functionality only as an
		// extra security check for NSIS users
		if (admin != null && !admin.isRegistrant() && person.isNsisAllowed()) {
			throw new RuntimeException("Kun registranter kan skifte kodeord på personer med en erhvervsidentitet!");
		}

		// replicate password to AD if enabled
		boolean replicateToAD = false;
		ADPasswordStatus adPasswordStatus = ADPasswordStatus.NOOP;
		
		/*
		 * we normally want to replicate the password change to Active Directory, but in certain cases we skip this
		 * - caller explicitly blocks replication (bypassReplication == true)
		 * - the person is prohibited from changing password in AD (person.isDoNotReplicatePassword() == true)
		 * - the person is from a domain that does not have a backend AD (person.getDomain().isStandalone == true)
		 */
		if (!bypassReplication && !person.isDoNotReplicatePassword() && !person.getDomain().isStandalone()) {
			if (StringUtils.hasLength(person.getSamaccountName())) {
				adPasswordStatus = passwordChangeQueueService.attemptPasswordChangeFromUI(person, newPassword, forceChangePassword);

				switch (adPasswordStatus) {
					case FAILURE:
					case TECHNICAL_ERROR:
					case INSUFFICIENT_PERMISSION:
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

			PasswordChangeQueue change = new PasswordChangeQueue(person, passwordChangeQueueService.encryptPassword(newPassword), forceChangePassword);
			change.setStatus(ReplicationStatus.DO_NOT_REPLICATE);

			passwordChangeQueueService.save(change);
		}

		// make sure we have an encoded password from here on
		String encodedPassword = encoder.encode(newPassword);

		// update password counter for this person
		// but only if the password was actually changed by the person themselves
		if (admin == null) {
			person.setDailyPasswordChangeCounter(person.getDailyPasswordChangeCounter() + 1);
		}

		// store password history
		PasswordHistory passwordHistory = new PasswordHistory();
		passwordHistory.setPerson(person);
		passwordHistory.setPassword(encodedPassword);
		passwordHistoryService.save(passwordHistory);

		// set new password locally
		person.setPassword(encodedPassword);
		person.setPasswordTimestamp(LocalDateTime.now());
		person.setLockedPassword(false);
		person.setLockedPasswordUntil(null);
		
		// reset bad-password indicator
		person.setBadPassword(false);
		person.setBadPasswordDeadlineTts(null);
		person.setBadPasswordReason(null);
		person.setBadPasswordRule(null);

		// if the changePassword was performed with a requested change-password-on-next-login, set that flag,
		// otherwise remove the flag if set already
		if (forceChangePassword) {
			person.setForceChangePassword(true);
		}
		else if (person.isForceChangePassword()) {
			person.setForceChangePassword(false);
		}

		// for students in Indskoling and SpecialNeeds classes we keep an encrypted copy for local usage,
		if (isStudentInIndskolingOrSpecialNeedsClass(person)) {
			try {
				String encrypted = passwordChangeQueueService.encryptPassword(newPassword);
				if (encrypted == null) {
					throw new UnsupportedEncodingException("Failed to encrypt password (null)");
				}

				person.setStudentPassword(encrypted);
				save(person);
			}
			catch (Exception ex) {
				log.error("Failed to store studentPassword on " + person.getId(), ex);
			}
		}

		save(person);

		// auditlog it
		if (parentCpr != null) {
			auditLogger.changePasswordByParent(person, parentCpr);
		}
		else if (admin != null) {
			auditLogger.changePasswordByAdmin(admin, person, replicateToAD);
		}
		else {
			auditLogger.changePasswordByPerson(person, replicateToAD);
		}

		return adPasswordStatus;
	}
	
	public boolean isStudent(Person person) {
		return person.getSchoolRoles().stream().anyMatch(r -> Objects.equals(r.getRole(), SchoolRoleValue.STUDENT));
	}
	
	public boolean isStudentInIndskolingOrSpecialNeedsClass(Person person) {
		List<SchoolRole> roles = person.getSchoolRoles().stream().filter(r -> Objects.equals(r.getRole(), SchoolRoleValue.STUDENT)).collect(Collectors.toList());
		
		for (SchoolRole role : roles) {
			for (SchoolRoleSchoolClassMapping clazzMapping : role.getSchoolClasses()) {
				SchoolClass clazz = clazzMapping.getSchoolClass();
				
				if (clazz.isIndskoling()) {
					return true;
				}
				
				if (schoolClassService.isSpecialNeedsClass(clazz)) {
					return true;
				}
			}
		}
		
		return false;
	}
	
	public ADPasswordStatus unlockAccount(Person person, Person performer) {
		ADPasswordStatus adPasswordStatus = ADPasswordStatus.NOOP;
		if (StringUtils.hasLength(person.getSamaccountName())) {
			adPasswordStatus = adPasswordService.attemptUnlockAccount(person);
			if (!ADPasswordResponse.isCritical(adPasswordStatus)) {
				person.setBadPasswordCount(0);
				person.setLockedPassword(false);
				person.setLockedPasswordUntil(null);
				if (performer != null) {
					auditLogger.unlockAccountByAnotherPerson(person, performer);
				} else {
					auditLogger.unlockAccountByPerson(person);
				}
				save(person);
			}
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

	@Transactional(rollbackFor = Exception.class)
	public void clearBadPasswordCounter() {
		List<Person> all = personDao.findByBadPasswordCountGreaterThan(0);

		if (all != null && all.size() > 0) {
			for (Person person : all) {
				person.setBadPasswordCount(0);
			}

			saveAll(all);
		}
	}

	public static String maskCpr(String cpr) {
		if (cpr != null && cpr.length() > 6) {
			return cpr.substring(0, 6) + "-XXXX";
		}

		return "";
	}

	public static boolean isOver18(String cpr) {
		long years = getAge(cpr);
		if (years < 18) {
			return false;
		}

		return true;
	}
	
	public static long getAge(String cpr) {
		// the get birthday localDate from cpr code is stolen from Sofd AuthorizationCodeService
		String day = cpr.substring(0, 2);
		String month = cpr.substring(2, 4);
		String yearString = cpr.substring(4, 6);
		int year = Integer.parseInt(yearString);

		switch (cpr.charAt(6)) {
			case '0':
			case '1':
			case '2':
			case '3':
				yearString = "19" + yearString;
				break;
			case '4':
			case '9':
				if (year <= 36) {
					yearString = "20" + yearString;
				}
				else {
					yearString = "19" + yearString;
				}
				break;
			case '5':
			case '6':
			case '7':
			case '8':
				if (year <= 57) {
					yearString = "20" + yearString;
				}
				else {
					yearString = "18" + yearString;
				}
				break;
			default:
				return -1;
		}

		String dateString = yearString + "-" + month + "-" + day;
		LocalDate date = null;
		try {
			date = LocalDate.parse(dateString);
		}
		catch (Exception ex) {
			return -1;
		}

		LocalDate now = LocalDate.now();
		
		return ChronoUnit.YEARS.between(date, now);
	}

	public void suspend(Person person) {
		person.setNsisLevel(NSISLevel.NONE);
		person.setApprovedConditions(false);
		person.setApprovedConditionsTts(null);
	}

	public static String getUsername(Person person) {
		if (person == null) {
			return null;
		}
		
		return person.getSamaccountName();
	}
	
	public static String getCorrectLockedPage(Person person) {
		if (person.isLockedExpired()) {
			return "error-expired-account";
		}
	
		return "error-locked-account";
	}

	public boolean requireApproveConditions(Person person) {
		if (person.getDomain().isNonNsis()) {
			return false;
		}

		if (!person.isApprovedConditions()) {
			return true;
		}

		if (person.getApprovedConditionsTts() == null) {
			return true;
		}

		LocalDateTime tts = termsAndConditionsService.getLastRequiredApprovedTts();
		if (tts == null) {
			return false;
		}

		if (person.getApprovedConditionsTts().isBefore(tts)) {
			return true;
		}

		return false;
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
		List<Person> personsToBeDeletedInMitIDErhverv = personsToBeDeleted.stream().filter(p -> StringUtils.hasLength(p.getNemloginUserUuid())).collect(Collectors.toList());

		for (Person person : personsToBeDeletedInMitIDErhverv) {
			nemloginQueueService.save(new NemloginQueue(person, NemloginAction.DELETE));
		}

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
	
	
	public Set<String> findDistinctAttributeNames() {
		return personDao.findDistinctAttributeNames();
	}
	
	@Transactional
	public void cleanupOldStudentsPasswords() {
		if (!commonConfiguration.getStilStudent().isEnabled()) {
			return;
		}
		
		List<Person> students = getAllStudentsWithStudentPassword();
		
		for (Person student : students) {
			SchoolClass passwordClass = isYoungStudent(student);
			
			// no longer a young student, so clear password
			if (passwordClass == null) {
				student.setStudentPassword(null);
				personDao.save(student);
			}
		}
	}

	@Transactional
	public void resetNSISLevelOnIncompleteActivations(){
		List<Person> peopleToFix = personDao.findByNsisLevelAndPasswordNull(NSISLevel.SUBSTANTIAL);
		for (Person p : peopleToFix) {
			p.setNsisLevel(NSISLevel.NONE);
		}

		if (!peopleToFix.isEmpty()) {
			log.info("Reset NSIS Level on " + peopleToFix.size() + " persons");
		}
		saveAll(peopleToFix);
	}

	public SchoolClass isYoungStudent(Person student) {
		// special feature to enable handling young students differently
		if (commonConfiguration.getStilStudent().isIndskolingSpecialEnabled()) {
			// assuming there is only one student role
			SchoolRole studentSchoolRole = student.getSchoolRoles().stream().filter(r -> r.getRole().equals(SchoolRoleValue.STUDENT)).findAny().orElse(null);
			if (studentSchoolRole == null) {
				return null;
			}
			
			for (SchoolRoleSchoolClassMapping schoolClassMapping : studentSchoolRole.getSchoolClasses()) {
				SchoolClass schoolClass = schoolClassMapping.getSchoolClass();
				
				// check for 'indskoling'
				if (schoolClass.isIndskoling()) {
					return schoolClass;
				}
				
				// check for special needs
				if (schoolClassService.isSpecialNeedsClass(schoolClass)) {
					return schoolClass;
				}
			}
		}

		return null;
	}

	public void deleteAll(List<Person> toDelete) {
		personDao.deleteAll(toDelete);		
	}

	@Cacheable("getChildren")
	public List<Person> getChildrenPasswordAllowed(String cpr) {
		List<Person> result = new ArrayList<>();
		
		Future<CprLookupDTO> cprFuture = cprService.getByCpr(cpr);
		CprLookupDTO personLookup = null;

		try {
			personLookup = (cprFuture != null) ? cprFuture.get(5, TimeUnit.SECONDS) : null;
		}
		catch (InterruptedException | ExecutionException | TimeoutException ex) {
			log.warn("Got a timeout on lookup of children", ex);
			return result;
		}

		if (personLookup != null && personLookup.getChildren() != null && !personLookup.getChildren().isEmpty()) {
			for (ChildDTO child : personLookup.getChildren()) {
				List<Person> childPersons = getByCpr(child.getCpr());

				for (Person person : childPersons) {
					if (person.isNsisAllowed()) {
						continue;
					}
					
					if (person.isLocked()) {
						continue;
					}
					
					if (isAdult(getBirthDateFromCpr(person.getCpr()))) {
						continue;
					}
					
					result.add(person);
				}
			}
		}

		return result;
	}

	private boolean isAdult(LocalDate birthday) {
		return LocalDate.from(birthday).until(LocalDate.now(), ChronoUnit.YEARS) >= 16;
	}

	private LocalDate getBirthDateFromCpr(String cpr) {
		var datePart = Integer.parseInt(cpr.substring(0, 2));
		var monthPart = Integer.parseInt(cpr.substring(2, 4));
		var yearPart = Integer.parseInt(cpr.substring(4, 6));
		var seventh = Integer.parseInt(cpr.substring(6, 7));
		var century = 0;
		
		if (seventh < 4) {
			century = 1900;
		}
		else if (seventh == 4 || seventh == 9) {
			century = yearPart < 37 ? 2000 : 1900;
		}
		else {
			century = yearPart < 58 ? 2000 : 1800;
		}
		
		return LocalDate.of(century + yearPart, monthPart, datePart);
	}

	public List<Person> getByExternalNemloginUserUuid(String uuid) {
		return personDao.findByExternalNemloginUserUuid(uuid);
	}

	public List<Person> findByLockedDatasetTrue() {
		return personDao.findByLockedDatasetTrue();
	}
}