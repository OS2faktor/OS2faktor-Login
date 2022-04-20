package dk.digitalidentity.common.service;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.PasswordSettingDao;
import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.PasswordSetting;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.service.enums.ChangePasswordResult;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PasswordSettingService {

	@Autowired
	private PasswordSettingDao passwordSettingDao;

	@Autowired
	private PasswordHistoryService passwordHistoryService;

	public ChangePasswordResult validatePasswordRules(Person person, String password) {
		if (person == null) {
			log.warn("Person is null!");
			return ChangePasswordResult.TECHNICAL_MISSING_PERSON;
		}

		Domain domain = person.getDomain();
		PasswordSetting settings = getSettings(domain);

		// Domain specific checks
		if (password.length() < settings.getMinLength()) {
			return ChangePasswordResult.TOO_SHORT;
		}

		if (settings.isRequireComplexPassword()) {
			int failures = 0;

			if (settings.isRequireLowercaseLetters() && !Pattern.compile("[a-zæøå]").matcher(password).find()) {
				failures++;
			}

			if (settings.isRequireUppercaseLetters() && !Pattern.compile("[A-ZÆØÅ]").matcher(password).find()) {
				failures++;
			}

			if (settings.isRequireDigits() && !Pattern.compile("\\d").matcher(password).find()) {
				failures++;
			}

			if (settings.isRequireSpecialCharacters() && !Pattern.compile("[^\\wæøå\\d]", Pattern.CASE_INSENSITIVE).matcher(password).find()) {
				failures++;
			}

			// only one missing rule is allowed here
			if (failures > 1) {
				return ChangePasswordResult.NOT_COMPLEX;
			}
		}
		else {
			if (settings.isRequireLowercaseLetters() && !Pattern.compile("[a-zæøå]").matcher(password).find()) {
				return ChangePasswordResult.NO_LOWERCASE;
			}

			if (settings.isRequireUppercaseLetters() && !Pattern.compile("[A-ZÆØÅ]").matcher(password).find()) {
				return ChangePasswordResult.NO_UPPERCASE;
			}

			if (settings.isRequireDigits() && !Pattern.compile("\\d").matcher(password).find()) {
				return ChangePasswordResult.NO_DIGITS;
			}

			if (settings.isRequireSpecialCharacters() && !Pattern.compile("[^\\wæøå\\d]", Pattern.CASE_INSENSITIVE).matcher(password).find()) {
				return ChangePasswordResult.NO_SPECIAL_CHARACTERS;
			}
		}

		if (settings.isDisallowDanishCharacters() && Pattern.compile("[æøåÆØÅ]").matcher(password).find()) {
			return ChangePasswordResult.DANISH_CHARACTERS_NOT_ALLOWED;
		}

		if (settings.isDisallowNameAndUsername()) {
			String lowerPwd = password.toLowerCase();
			for (String name : person.getName().toLowerCase().split(" ")) {
				if (lowerPwd.contains(name)) {
					return ChangePasswordResult.CONTAINS_NAME;
				}
			}

			String sAMAccountName = person.getSamaccountName();
			if (sAMAccountName != null && lowerPwd.contains(sAMAccountName.toLowerCase())) {
				return ChangePasswordResult.CONTAINS_NAME;
			}
		}

		if (settings.isDisallowOldPasswords()) {
			BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

			List<String> lastTenPasswords = passwordHistoryService.getLastTenPasswords(person);
			for (String oldPassword : lastTenPasswords) {
				if (encoder.matches(password, oldPassword)) {
					return ChangePasswordResult.OLD_PASSWORD;
				}
			}
		}

		return ChangePasswordResult.OK;
	}

	public PasswordSetting getSettings(Domain domain) {
		List<PasswordSetting> all = passwordSettingDao.findByDomain(domain);

		if (all.size() == 0) {
			PasswordSetting settings = new PasswordSetting();
			settings.setMinLength(10L);
			settings.setRequireComplexPassword(false);
			settings.setRequireLowercaseLetters(true);
			settings.setRequireUppercaseLetters(false);
			settings.setRequireDigits(false);
			settings.setRequireSpecialCharacters(false);
			settings.setDisallowDanishCharacters(false);
			settings.setDisallowNameAndUsername(false);
			settings.setValidateAgainstAdEnabled(true);
			settings.setDomain(domain);

			return settings;
		}
		else if (all.size() == 1) {
			return all.get(0);
		}

		log.error("More than one row with password rules for domain: " + domain.getName());
		return all.get(0);
	}

	public List<PasswordSetting> getAllSettings() {
		return passwordSettingDao.findAll();
	}

	public List<PasswordSetting> getSettingsByChangePasswordOnUsersEnabled() {
		return passwordSettingDao.findByChangePasswordOnUsersEnabledTrue();
	}

	public PasswordSetting save(PasswordSetting entity) {
		return passwordSettingDao.save(entity);
	}
}
