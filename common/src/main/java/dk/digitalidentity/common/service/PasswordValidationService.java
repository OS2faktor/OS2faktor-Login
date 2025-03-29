package dk.digitalidentity.common.service;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.EmailTemplate;
import dk.digitalidentity.common.dao.model.EmailTemplateChild;
import dk.digitalidentity.common.dao.model.PasswordSetting;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.BadPasswordReason;
import dk.digitalidentity.common.dao.model.enums.EmailTemplateType;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.enums.ChangePasswordResult;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PasswordValidationService {

	@Autowired
	private BadPasswordService badPasswordService;

	@Autowired
	private PasswordHistoryService passwordHistoryService;

	@Autowired
	private AuditLogger auditLogger;
	
	@Autowired
	private CommonConfiguration commonConfiguration;
	
	@Autowired
	private PersonService personService;

	@Autowired
	private EmailTemplateService emailTemplateService;
	
	@Autowired
	private MessageQueueService messageQueueService;
	
	@Autowired
	private PasswordSettingService passwordSettingService;

	public ChangePasswordResult validatePasswordRules(Person person, String password, boolean auditlogFailures) {
		ChangePasswordResult result = validate(person, password, false);
		
		if (auditlogFailures && result != ChangePasswordResult.OK) {
			auditLogger.changePasswordFailed(person, result.getMessage());
		}
		
		return result;
	}

	// only use this for pre-validation (or when enrolling an existing password that does not need external validation)
	public ChangePasswordResult validatePasswordRulesWithoutSlowValidationRules(Person person, String password) {
		return validate(person, password, true);
	}

	private ChangePasswordResult validate(Person person, String password, boolean skipSlowValidation) {
		if (person == null) {
			log.warn("Person is null!");
			return ChangePasswordResult.TECHNICAL_MISSING_PERSON;
		}

		if (!StringUtils.hasLength(password)) {
			log.warn("Password was null or empty");
			return ChangePasswordResult.TOO_SHORT;
		}

		Domain domain = person.getDomain();
		PasswordSetting settings = passwordSettingService.getSettingsCached(domain);

		// Domain specific checks
		if (password.length() < settings.getMinLength()) {
			return ChangePasswordResult.TOO_SHORT;
		}

		if (password.length() > settings.getMaxLength()) {
			return ChangePasswordResult.TOO_LONG;
		}

		if (settings.isPreventBadPasswords() && badPasswordService.match(password)) {
			return ChangePasswordResult.BAD_PASSWORD;
		}

		if (settings.isRequireComplexPassword()) {
			int failures = 0;

			if (!Pattern.compile("[a-zæøå]").matcher(password).find()) {
				failures++;
			}

			if (!Pattern.compile("[A-ZÆØÅ]").matcher(password).find()) {
				failures++;
			}

			if (!Pattern.compile("\\d").matcher(password).find()) {
				failures++;
			}

			// check for existence of special characters, with the exception that the Danish letters ÆØÅ does not count
			// as special characters. Other characters from other languages WILL count as special characters
			if (!Pattern.compile("[^\\wæøå\\d]", Pattern.CASE_INSENSITIVE).matcher(password).find()) {
				failures++;
			}

			if (containsName(person, password)) {
				return ChangePasswordResult.CONTAINS_NAME;
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

			// check for existence of special characters, with the exception that the Danish letters ÆØÅ does not count
			// as special characters. Other characters from other languages WILL count as special characters
			if (settings.isRequireSpecialCharacters() && !Pattern.compile("[^\\wæøå\\d]", Pattern.CASE_INSENSITIVE).matcher(password).find()) {
				return ChangePasswordResult.NO_SPECIAL_CHARACTERS;
			}
		}

		if (settings.isDisallowDanishCharacters() && Pattern.compile("[æøåÆØÅ]").matcher(password).find()) {
			return ChangePasswordResult.DANISH_CHARACTERS_NOT_ALLOWED;
		}

		if (settings.isDisallowNameAndUsername()) {
			if (containsName(person, password)) {
				return ChangePasswordResult.CONTAINS_NAME;
			}
		}

		if (settings.isSpecificSpecialCharactersEnabled()) {
			StringBuilder regEx = new StringBuilder("[^a-zA-Z0-9æøåÆØÅ");
			char[] allowedCharacters = settings.getAllowedSpecialCharacters().toCharArray();
			StringBuilder escapedRegEx = new StringBuilder();

			// add \ to every character to escape them properly
			for (char allowedCharacter : allowedCharacters) {
				escapedRegEx.append("\\").append(allowedCharacter);
			}

			String test = Pattern.quote(escapedRegEx.toString());
			regEx.append(test).append("]");
			if (Pattern.compile(regEx.toString()).matcher(password).find()) {
				return ChangePasswordResult.WRONG_SPECIAL_CHARACTERS;
			}
		}

		if (!skipSlowValidation) {
			if (settings.isDisallowOldPasswords()) {
				BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
	
				List<String> lastXPasswords = passwordHistoryService.getLastXPasswords(person);
				for (String oldPassword : lastXPasswords) {
					if (encoder.matches(password, oldPassword)) {
						return ChangePasswordResult.OLD_PASSWORD;
					}
				}
			}
	
			if (settings.isCheckLeakedPasswords() && isPasswordLeaked(person, password)) {
				return ChangePasswordResult.LEAKED_PASSWORD;
			}
		}

        return ChangePasswordResult.OK;
	}

	@Async
	@Transactional
	public CompletableFuture<Void> isPasswordLeakedAsync(Person person, String password) {
		boolean result = isPasswordLeaked(person, password);
		if (result) {
			person.setBadPassword(true);
			person.setBadPasswordDeadlineTts(LocalDate.now().plusDays(commonConfiguration.getFullServiceIdP().getPasswordLeakConformityGracePeriod()));
			person.setBadPasswordReason(BadPasswordReason.LEAKED);
			
			auditLogger.badPasswordMustChange(person, null);
			
			EmailTemplate emailTemplate = emailTemplateService.findByTemplateType(EmailTemplateType.PASSWORD_LEAKED);
			if (emailTemplate.getChildren().stream().anyMatch(c -> c.isEnabled())) {
				for (EmailTemplateChild emailTemplateChild : emailTemplate.getChildren()) {
					if (emailTemplateChild.isEnabled() && emailTemplateChild.getDomain().getId() == person.getDomain().getId()) {
						String msg = EmailTemplateService.safeReplacePlaceholder(emailTemplateChild.getMessage(), EmailTemplateService.RECIPIENT_PLACEHOLDER, person.getName());
						msg = EmailTemplateService.safeReplacePlaceholder(msg, EmailTemplateService.USERID_PLACEHOLDER, person.getSamaccountName());

						// TODO: move EmailTemplateSenderService to common, so we can reuse the code from there

						if (emailTemplateChild.isEboks()) {
							messageQueueService.queueEboks(person, emailTemplateChild.getTitle(), msg);
						}
						
						if (emailTemplateChild.isEmail() && StringUtils.hasLength(person.getEmail())) {
							messageQueueService.queueEmail(person, emailTemplateChild.getTitle(), msg);
						}
					}
				}
			}
		}
		else {
			person.setBadPasswordLeakCheckTts(LocalDate.now());
		}

		personService.save(person);

		return CompletableFuture.completedFuture(null);
	}

	private boolean isPasswordLeaked(Person person, String password) {
		try {
			String hashText = getHashOfPassword(password);
			if (hashText != null) {
				String prefix = hashText.substring(0, 5);
				String suffix = hashText.substring(5);

				List<String> suffixes = callPwnedApi(prefix);

				if (suffixes.contains(suffix)) {
					log.warn("Detected leaked password for " + person.getSamaccountName());
					return true;
				}
			}
		}
		catch (Exception ex) {
			log.error("Problem checking if password is leaked", ex);
		}

		return false;
	}

	private List<String> callPwnedApi(String prefix) throws Exception {
		RestTemplate restTemplate = new RestTemplate();

		String url = "https://api.pwnedpasswords.com/range/" + prefix;

		ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
		if (response.getStatusCode().value() == 200) {
			String tokens[] = response.getBody().split("\n");
			
			List<String> result = new ArrayList<>();
			for (String token : tokens) {
				result.add(token.split(":")[0].trim());
			}
			
			return result;
		}
		
		throw new Exception("Unable to get response from https://api.pwnedpasswords.com/range/" + prefix + " : statusCode = " + response.getStatusCode().value());
	}

	private String getHashOfPassword(String password) throws Exception {
		if (!StringUtils.hasLength(password)) {
			return null;
		}

		MessageDigest sha = MessageDigest.getInstance("SHA-1");
		byte[] hashedPassword = sha.digest(password.getBytes());

		BigInteger no = new BigInteger(1, hashedPassword);

		// convert message digest into hex value
		String hashtext = no.toString(16);

		// add preceding 0s to make it 32 bit
		while (hashtext.length() < 32) {
			hashtext = "0" + hashtext;
		}

		return hashtext.toUpperCase();
	}

	private boolean containsName(Person person, String password) {
		String lowerPwd = password.toLowerCase();
		for (String name : person.getName().toLowerCase().split(" ")) {
			if (StringUtils.hasLength(name) && name.length() > 2 && lowerPwd.contains(name)) {
				return true;
			}
		}

		String sAMAccountName = person.getSamaccountName();
		if (sAMAccountName != null && lowerPwd.contains(sAMAccountName.toLowerCase())) {
			return true;
		}
		return false;
	}
}
