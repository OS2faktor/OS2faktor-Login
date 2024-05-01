package dk.digitalidentity.service;

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
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.service.ADPasswordService;
import dk.digitalidentity.common.service.PasswordChangeQueueService;
import dk.digitalidentity.common.service.PasswordSettingService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.model.ADPasswordResponse;
import dk.digitalidentity.common.service.model.ADPasswordResponse.ADPasswordStatus;
import dk.digitalidentity.service.model.enums.PasswordStatus;
import dk.digitalidentity.service.model.enums.PasswordValidationResult;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PasswordService {
	private enum CacheStrategy { NO_AD, NO_CACHE_UNLESS_AD_DOWN, NO_CACHE, WITH_CACHE };
	
	@Autowired
	private PersonService personService;

	@Autowired
	private SessionHelper sessionHelper;

	@Autowired
	private PasswordSettingService passwordSettingService;

	@Autowired
	private ADPasswordService adPasswordService;
	
	@Autowired
	private PasswordChangeQueueService passwordChangeQueueService;

	@Autowired
	private CommonConfiguration commonConfiguration;

	/**
	 * attempt to validate password in this order (until successful)
	 * 
	 * 1) against NSIS password
	 */
	public PasswordValidationResult validatePasswordNoAD(String password, Person person) {
		return validatePassword(password, person, false, CacheStrategy.NO_AD);
	}

	// note that this is the most common method - and should be considered the default unless a different behaviour is needed
	/**
	 * attempt to validate password in this order (until successful)
	 * 
	 * 1) against NSIS password
	 * 2) against AD password (and then update the cached password if successful)
	 * 3) against AD cached password (only if no connection to AD is available)
	 */
	public PasswordValidationResult validatePasswordNoCacheUnlessADDown(String password, Person person) {
		return validatePassword(password, person, false, CacheStrategy.NO_CACHE_UNLESS_AD_DOWN);
	}
	
	/**
	 * attempt to validate password in this order (until successful)
	 * 
	 * 1) against NSIS password
	 * 2) against AD password (and then update the cached password if successful)
	 */
	public PasswordValidationResult validatePassword(String password, Person person) {
		return validatePassword(password, person, false, CacheStrategy.NO_CACHE);
	}

	/**
	 * attempt to validate password in this order (until successful)
	 * 
	 * 1) against NSIS password
	 * 2) against AD cached password (and no fallback to direct validation)
	 * 
	 * note it will return FAILURE in cache-validation misses non non-nsis users, but it will not increment error count, nor block the account
	 */
	public PasswordValidationResult validatePasswordWithCache(String password, Person person, boolean isWcp) {
		return validatePassword(password, person, isWcp, CacheStrategy.WITH_CACHE);
	}
	
	public boolean hasCachedADPassword(Person person) {
		return adPasswordService.hasCachedADPassword(person);
	}

	public PasswordStatus getPasswordStatus(Person person) {
		// Regardless of password policy of forcing people to change password every X days
		// we always force it if the flag on the person has been set to true
		if (person.isForceChangePassword()) {
			return PasswordStatus.FORCE_CHANGE;
		}

		// Any other prompting of password change needs PasswordSetting.isForceChangePasswordEnabled=true
		PasswordSetting settings = passwordSettingService.getSettingsCached(person.getDomain());
		if (settings.isForceChangePasswordEnabled()) {
			// Person is in weird state from not completing activation fully
			if (person.hasActivatedNSISUser() && (person.getNsisPasswordTimestamp() == null || !StringUtils.hasLength(person.getNsisPassword()))) {
				log.warn("Person: " + person.getUuid() + " has no NSIS password");

				// reset persons NSIS level if set - they skipped an important step during activation
				if (NSISLevel.LOW.equalOrLesser(person.getNsisLevel())) {
					person.setNsisLevel(NSISLevel.NONE);
					personService.save(person);
				}

				// inform caller that the person has no password, so they can be dropped into activation
				return PasswordStatus.NO_PASSWORD;
			}

			// NSIS Password expired
			if (person.hasActivatedNSISUser()) {
				LocalDateTime expiredTimestamp = LocalDateTime.now().minusDays(settings.getForceChangePasswordInterval());
				if (person.getNsisPasswordTimestamp().isBefore(expiredTimestamp)) {
					return PasswordStatus.EXPIRED;
				}
			}

			// NextPasswordChange expired (from CoreDataAPI)
			if (person.getNextPasswordChange() != null && person.getNextPasswordChange().isBefore(LocalDateTime.now())) {
				return PasswordStatus.EXPIRED;
			}

			// NSIS Password almost expired
			if (person.hasActivatedNSISUser()) {
				LocalDateTime almostExpiredTimestamp = LocalDateTime.now().minusDays(settings.getForceChangePasswordInterval()).plusDays(commonConfiguration.getPasswordSoonExpire().getReminderDaysBeforeExpired());
				if (person.getNsisPasswordTimestamp().isBefore(almostExpiredTimestamp)) {
					return PasswordStatus.ALMOST_EXPIRED;
				}
			}

			// NextPasswordChange almost expired (from CoreDataAPI)
			if (person.getNextPasswordChange() != null) {
				LocalDateTime almostExpiredTimestamp = person.getNextPasswordChange().minusDays(commonConfiguration.getPasswordSoonExpire().getReminderDaysBeforeExpired());
				if (!LocalDateTime.now().isBefore(almostExpiredTimestamp)) {
					return PasswordStatus.ALMOST_EXPIRED;
				}
			}
		}

		return PasswordStatus.OK;
	}

	private PasswordValidationResult validatePassword(String password, Person person, boolean isWcp, CacheStrategy cacheStrategy) {

		// special case - if no password is supplied, just abort. This does not count as an invalid attempt though,
		// so no increase in bad_password_attempts
		if (!StringUtils.hasLength(password)) {
			sessionHelper.setPasswordLevel(null);
			return PasswordValidationResult.INVALID;
		}

		// if the account is locked, we just abort here, and we do not log an invalid password attempt, so
		// no actual validation is performed (the password might be correct, but it IS locked)
		if (person.isLockedPassword()) {
			sessionHelper.setPasswordLevel(null);
			return PasswordValidationResult.LOCKED;
		}
		
		// now lets compute the actual result
		PasswordValidationResult result = null;

		// if the person has an NSIS password, we always start with validating against that password
		if (person.hasActivatedNSISUser() && StringUtils.hasLength(person.getNsisPassword())) {
			BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
			PasswordSetting settings = passwordSettingService.getSettingsCached(person.getDomain());

			if (encoder.matches(password, person.getNsisPassword())) {
				// Password matches, check for expiry

				if (settings.isForceChangePasswordEnabled() && person.getNsisPasswordTimestamp() != null) {
					LocalDateTime maxPasswordAge = LocalDateTime.now().minusDays(settings.getForceChangePasswordInterval());

					if (!person.getNsisPasswordTimestamp().isAfter(maxPasswordAge)) {
						log.warn("Local password validation failed due to expired password for person " + person.getId());

						result = PasswordValidationResult.VALID_EXPIRED;
					}
				}

				// Password matches and not expired
				if (result == null) {
					sessionHelper.setPasswordLevel(NSISLevel.SUBSTANTIAL);
					sessionHelper.setAuthnInstant(new DateTime());
					result = PasswordValidationResult.VALID;
				}
			}
			else if (cacheStrategy == CacheStrategy.NO_AD) {
				// if we are not going to fallback to AD validation, the result is now invalid (otherwise fallback to AD validation)
				result = PasswordValidationResult.INVALID;
			}
		}
		
		/* for students, with a stored student password, we allow validating against the local student password if either
		 *  - the student is indskoling or special needs class
		 *  - or the domain of the student is standAlone
		 */
		if ((result == null && personService.isStudent(person) && StringUtils.hasLength(person.getStudentPassword())) &&
			(personService.isStudentInIndskolingOrSpecialNeedsClass(person) || person.getDomain().isStandalone())) {

			try {
				String pwd = passwordChangeQueueService.decryptPassword(person.getStudentPassword());
				
				if (Objects.equals(pwd, password)) {
					sessionHelper.setAuthenticatedWithADPassword(true);
					sessionHelper.setADPerson(person);
					sessionHelper.setPasswordLevel(NSISLevel.NONE);
					sessionHelper.setAuthnInstant(new DateTime());
					sessionHelper.setPassword(password);
					
					// not actually a cache, as this is the student pwd database
					result = PasswordValidationResult.VALID;
				}
			}
			catch (Exception ex) {
				log.error("Failed to decrypt student password for " + person.getId(), ex);
			}
		}

		// fallback to validating against AD (if allowed and possible...)
		if (result == null && StringUtils.hasLength(person.getSamaccountName()) && !person.getDomain().isStandalone()) {
			PasswordSetting topLevelDomainPasswordSettings = passwordSettingService.getSettingsCached(person.getTopLevelDomain());

			if (topLevelDomainPasswordSettings.isValidateAgainstAdEnabled()) {
				switch (cacheStrategy) {
					case NO_AD:
						break;
					case NO_CACHE: {
						ADPasswordStatus adResult = adPasswordService.validatePassword(person, password);

						if (ADPasswordResponse.ADPasswordStatus.OK.equals(adResult)) {
							sessionHelper.setAuthenticatedWithADPassword(true);
							sessionHelper.setADPerson(person);
							sessionHelper.setPasswordLevel(NSISLevel.NONE);
							sessionHelper.setAuthnInstant(new DateTime());
							sessionHelper.setPassword(password);
							result = PasswordValidationResult.VALID;
	
							// update the cached password then
							adPasswordService.updateCache(person, password);
						}

						break;
					}
					case WITH_CACHE: {
						// check cache on technical/timeout errors only
						if (adPasswordService.validateAgainstCache(person, password)) {
							sessionHelper.setAuthenticatedWithADPassword(true);
							sessionHelper.setADPerson(person);
							sessionHelper.setPasswordLevel(NSISLevel.NONE);
							sessionHelper.setAuthnInstant(new DateTime());
							sessionHelper.setPassword(password);
							result = PasswordValidationResult.VALID_CACHE;							
						}
						else {
							// special case - the user does not actually have a registered NSIS password
							// so the validation against the cache is the only thing we are actually doing,
							// and a cache-miss does not count as an invalid password attempt, just reject
							// without counting up anything
							if (!StringUtils.hasLength(person.getNsisPassword())) {
								sessionHelper.setPasswordLevel(null);
								return PasswordValidationResult.INVALID;
							}
						}

						break;
					}
					case NO_CACHE_UNLESS_AD_DOWN: {
						ADPasswordStatus adResult = adPasswordService.validatePassword(person, password);

						// first attempt validation against AD
						if (ADPasswordResponse.ADPasswordStatus.OK.equals(adResult)) {
							sessionHelper.setAuthenticatedWithADPassword(true);
							sessionHelper.setADPerson(person);
							sessionHelper.setPasswordLevel(NSISLevel.NONE);
							sessionHelper.setAuthnInstant(new DateTime());
							sessionHelper.setPassword(password);
							result = PasswordValidationResult.VALID;
	
							// update the cached password then
							adPasswordService.updateCache(person, password);
						}
						else if (adResult == ADPasswordStatus.TECHNICAL_ERROR || adResult == ADPasswordStatus.TIMEOUT) {

							// check cache on technical/timeout errors only
							if (adPasswordService.validateAgainstCache(person, password)) {
								sessionHelper.setAuthenticatedWithADPassword(true);
								sessionHelper.setADPerson(person);
								sessionHelper.setPasswordLevel(NSISLevel.NONE);
								sessionHelper.setAuthnInstant(new DateTime());
								sessionHelper.setPassword(password);
								result = PasswordValidationResult.VALID_CACHE;							
							}
						}

						break;
					}
				}
			}
		}

		// still no result from any validation, well then the password is invalid :(
		if (result == null) {
			sessionHelper.setPasswordLevel(null);
			result = PasswordValidationResult.INVALID;
		}

		if (PasswordValidationResult.INVALID.equals(result)) {
			personService.badPasswordAttempt(person, isWcp);
		}

		return result;
	}
}
