package dk.digitalidentity.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.PasswordSetting;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.BadPasswordReason;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.ADPasswordService;
import dk.digitalidentity.common.service.PasswordChangeQueueService;
import dk.digitalidentity.common.service.PasswordSettingService;
import dk.digitalidentity.common.service.PasswordValidationService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.enums.ChangePasswordResult;
import dk.digitalidentity.common.service.model.ADPasswordResponse;
import dk.digitalidentity.common.service.model.ADPasswordResponse.ADPasswordStatus;
import dk.digitalidentity.service.model.enums.PasswordExpireStatus;
import dk.digitalidentity.service.model.enums.PasswordValidationResult;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PasswordService {
	
	@Autowired
	private PersonService personService;

	@Autowired
	private SessionHelper sessionHelper;

	@Autowired
	private PasswordSettingService passwordSettingService;

	@Autowired
	private PasswordValidationService passwordValidationService;

	@Autowired
	private ADPasswordService adPasswordService;
	
	@Autowired
	private PasswordChangeQueueService passwordChangeQueueService;

	@Autowired
	private CommonConfiguration commonConfiguration;

	@Autowired
	private AuditLogger auditLogger;

	/**
	 * attempt to validate password in this order (until successful)
	 * 
	 * 1) against person.getPassword()
	 */
	public PasswordValidationResult validatePasswordFromWcp(String password, Person person) {
		return validatePassword(password, person, true, false, true);
	}

	/**
	 * attempt to validate password in this order (until successful)
	 * 
	 * 1) against person.getPassword()
	 */
	public PasswordValidationResult validatePasswordNoAD(String password, Person person) {
		return validatePassword(password, person, false, false, true);
	}

	/**
	 * attempt to validate password in this order (until successful)
	 * 
	 * 1) against person.getPassword()
	 * 2) against AD password
	 */
	public PasswordValidationResult validatePassword(String password, Person person) {
		return validatePassword(password, person, false, true, true);
	}
	
	/**
	 * just validate the password, but do not create any session (nor invalidate any)
	 */
	public PasswordValidationResult validatePasswordWithoutLogin(String password, Person person) {
		return validatePassword(password, person, false, true, false);
	}
	
	public PasswordExpireStatus getPasswordExpireStatus(Person person) {
		// Regardless of password policy of forcing people to change password every X days
		// we always force it if the flag on the person has been set to true
		if (person.isForceChangePassword()) {
			return PasswordExpireStatus.FORCE_CHANGE;
		}

		// Any other prompting of password change needs PasswordSetting.isForceChangePasswordEnabled=true
		PasswordSetting settings = passwordSettingService.getSettings(person);
		if (settings.isForceChangePasswordEnabled()) {

			// person does not actually have a password set (yet)
			if (person.getPasswordTimestamp() == null || !StringUtils.hasLength(person.getPassword())) {

				// reset persons NSIS level if set - they skipped an important step during activation
				if (NSISLevel.LOW.equalOrLesser(person.getNsisLevel())) {
					log.warn("Person: " + person.getUuid() + " has an activated NSIS account, but no password");

					person.setNsisLevel(NSISLevel.NONE);
					personService.save(person);
					
					// this special state is an error-indicator, telling the caller to drop the user into activation again, so
					// the user can get a registered password
					return PasswordExpireStatus.NO_PASSWORD;
				}

				// no password is okay when it comes to state - the caller should just validate against AD if possible
				return PasswordExpireStatus.OK;
			}

			// password expired
			LocalDateTime expiredTimestamp = LocalDateTime.now().minusDays(settings.getForceChangePasswordInterval());
			if (person.getPasswordTimestamp().isBefore(expiredTimestamp)) {
				return PasswordExpireStatus.EXPIRED;
			}

			// password almost expired
			LocalDateTime almostExpiredTimestamp = LocalDateTime.now().minusDays(settings.getForceChangePasswordInterval()).plusDays(commonConfiguration.getPasswordSoonExpire().getReminderDaysBeforeExpired());
			if (person.getPasswordTimestamp().isBefore(almostExpiredTimestamp)) {
				return PasswordExpireStatus.ALMOST_EXPIRED;
			}
		}

		return PasswordExpireStatus.OK;
	}

	private PasswordValidationResult validatePassword(String password, Person person, boolean isWcp, boolean allowFallbackToAD, boolean modifySession) {
		
		// special case - if no password is supplied, just abort. This does not count as an invalid attempt though,
		// so no increase in bad_password_attempts
		if (!StringUtils.hasLength(password)) {
			if (modifySession) {
				sessionHelper.setPasswordLevel(null);
			}
			return PasswordValidationResult.INVALID;
		}

		// if the account is locked, we just abort here, and we do not log an invalid password attempt, so
		// no actual validation is performed (the password might be correct, but it IS locked)
		if (person.isLockedPassword()) {
			if (modifySession) {
				sessionHelper.setPasswordLevel(null);
			}
			return PasswordValidationResult.LOCKED;
		}
		
		// now lets compute the actual result
		PasswordValidationResult result = null;

		// if the person has a registered password, we always start with validating against that password
		if (StringUtils.hasLength(person.getPassword())) {
			BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

			if (encoder.matches(password, person.getPassword())) {

				// password matches, check for expiry
				PasswordExpireStatus passwordStatus = getPasswordExpireStatus(person);
				switch (passwordStatus) {
					//ALMOST is not EXPIRED - so nope
					//case ALMOST_EXPIRED:
					case EXPIRED:
					case FORCE_CHANGE:
						log.warn("Local password validation failed due to expired password for person " + person.getId());

						if (modifySession) {
							sessionHelper.setPasswordLevel(person.getNsisLevel());
							sessionHelper.setAuthnInstant(new DateTime());
						}

						result = PasswordValidationResult.VALID_EXPIRED;
					default:
						break;
				}

				// Password matches and not expired
				if (result == null) {
					// when validating against the stored password, we set the sessions password-nsis-level to the persons level,
					// which ensures that we set it to NONE or SUBSTANTIAL, depending on the type of user that logged in.
					// note that when we validate against AD further down, it is always set to NONE

					if (modifySession) {
						sessionHelper.setPasswordLevel(person.getNsisLevel());
						sessionHelper.setAuthnInstant(new DateTime());
					}
					
					result = PasswordValidationResult.VALID;
				}
			}
			else if (!allowFallbackToAD) {
				// if we are not going to fallback to AD validation, the result is now invalid (otherwise fallback to AD validation)
				result = PasswordValidationResult.INVALID;
			}
		}

		/*
		 * for students that are in indskoling or special-needs-class, we validate against this field as fallback, as
		 * we store a copy of their password, which can be shown to the teacher in the UI
		 */
		if (result == null && StringUtils.hasLength(person.getStudentPassword()) && personService.isStudent(person) && personService.isStudentInIndskolingOrSpecialNeedsClass(person)) {
			try {
				String pwd = passwordChangeQueueService.decryptPassword(person.getStudentPassword());
				
				if (Objects.equals(pwd, password)) {
					if (modifySession) {
						sessionHelper.setAuthenticatedWithADPassword(true);
						sessionHelper.setADPerson(person);
						sessionHelper.setPasswordLevel(NSISLevel.NONE);
						sessionHelper.setAuthnInstant(new DateTime());
						sessionHelper.setPassword(password);
					}
					
					// not actually a cache, as this is the student pwd database
					result = PasswordValidationResult.VALID;
				}
			}
			catch (Exception ex) {
				log.error("Failed to decrypt student password for " + person.getId(), ex);
			}
		}

		// fallback to validating against AD unless it is a standalone domain (or explicitly prohibited by caller)
		boolean noValidationNeededDueToFallbackPasswordNotStoredInDatabase = false;
		if (result == null && allowFallbackToAD && !person.getDomain().isStandalone()) {
			ADPasswordStatus adResult = adPasswordService.validatePassword(person, password);

			if (ADPasswordResponse.ADPasswordStatus.OK.equals(adResult)) {
				if (modifySession) {
					sessionHelper.setAuthenticatedWithADPassword(true);
					sessionHelper.setADPerson(person);
					sessionHelper.setPasswordLevel(NSISLevel.NONE);
					sessionHelper.setAuthnInstant(new DateTime());
					sessionHelper.setPassword(password);
				}

				result = PasswordValidationResult.VALID;

				// if the person is a non-nsis user, we also store the password in the database for later validation purposes
				if (!person.hasActivatedNSISUser()) {
					BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

					person.setPassword(encoder.encode(password));
					person.setPasswordTimestamp(LocalDateTime.now());

					// reset bad-password indicator
					person.setBadPassword(false);
					person.setBadPasswordDeadlineTts(null);
					person.setBadPasswordReason(null);
					person.setBadPasswordRule(null);

					personService.save(person);
				}
				else {
					noValidationNeededDueToFallbackPasswordNotStoredInDatabase = true;
				}
			}
		}

		// still no result from any validation, well then the password is invalid :(
		if (result == null) {
			if (modifySession) {
				sessionHelper.setPasswordLevel(null);
			}
			result = PasswordValidationResult.INVALID;
		}

		// if no errors where encountered, and the password used is the one we store in the database, make sure
		// it complies with the current complexity requirements
		if (!noValidationNeededDueToFallbackPasswordNotStoredInDatabase && result.isNoErrors()) {

			// if password already flagged as leaked, we will skip any further validation
			if (person.isBadPassword() && person.getBadPasswordReason() == BadPasswordReason.LEAKED) {

				// if we have passed the grace period, we will block usage - password needs to be changed using MitID
				if (LocalDate.now().isAfter(person.getBadPasswordDeadlineTts())) {
					result = PasswordValidationResult.INVALID_BAD_PASSWORD;
				}
				else {
					result = PasswordValidationResult.VALID_BUT_BAD_PASWORD;
				}				
			}
			else {

				// perform password complexity control now
				ChangePasswordResult changePasswordResult = passwordValidationService.validatePasswordRulesWithoutSlowValidationRules(person, password);
				if (changePasswordResult != ChangePasswordResult.OK) {

					// flag user if not already flagged
					if (person.getBadPasswordDeadlineTts() == null || person.getBadPasswordReason() == null || person.getBadPasswordDeadlineTts() == null) {
						person.setBadPassword(true);
						person.setBadPasswordReason(BadPasswordReason.COMPLEXITY);
						person.setBadPasswordRule(changePasswordResult);
						person.setBadPasswordDeadlineTts(LocalDate.now().plusDays(commonConfiguration.getFullServiceIdP().getPasswordComplexityConformityGracePeriod()));
						personService.save(person);
						
						auditLogger.badPasswordMustChange(person, changePasswordResult);
					}
					
					// if we have passed the grace period, we will block usage - password needs to be changed using MitID
					if (LocalDate.now().isAfter(person.getBadPasswordDeadlineTts())) {
						result = PasswordValidationResult.INVALID_BAD_PASSWORD;
					}
					else {
						result = PasswordValidationResult.VALID_BUT_BAD_PASWORD;
					}
				}
				else {
					// just in case the person was flagged, we remove the flag (e.g. if the password complexity has been reduced, and
					// they where previously flagged, we will remove that flag now
					if (person.isBadPassword() && person.getBadPasswordReason() == BadPasswordReason.COMPLEXITY) {
						person.setBadPassword(false);
						person.setBadPasswordReason(null);
						person.setBadPasswordRule(null);
						person.setBadPasswordDeadlineTts(null);
						personService.save(person);
					}
				}
				
				// if password leakage control is enabled, perform a background check if needed
				PasswordSetting passwordSetting = passwordSettingService.getSettings(person);
				if (passwordSetting.isCheckLeakedPasswords()) {
					// only check every X days according to settings
					if (person.getBadPasswordLeakCheckTts() == null ||
						LocalDate.now().minusDays(commonConfiguration.getFullServiceIdP().getPasswordLeakCheckInterval()).isAfter(person.getBadPasswordLeakCheckTts())) {
						
						passwordValidationService.isPasswordLeakedAsync(person, password);
					}
				}
			}
		}
		
		if (PasswordValidationResult.INVALID.equals(result)) {
			personService.badPasswordAttempt(person, isWcp);
		}

		return result;
	}
}
