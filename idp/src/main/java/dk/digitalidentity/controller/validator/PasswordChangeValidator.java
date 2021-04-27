package dk.digitalidentity.controller.validator;

import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.PasswordSetting;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.service.PasswordSettingService;
import dk.digitalidentity.controller.dto.PasswordChangeForm;
import java.util.Objects;
import java.util.regex.Pattern;

import dk.digitalidentity.service.SessionHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

@Component
public class PasswordChangeValidator implements Validator {

	@Autowired
	private PasswordSettingService passwordSettingService;

	@Autowired
	private SessionHelper sessionHelper;

	@Override
	public boolean supports(Class<?> aClass) {
		return (PasswordChangeForm.class.isAssignableFrom(aClass));
	}

	@Override
	public void validate(Object o, Errors errors) {
		PasswordChangeForm form = (PasswordChangeForm) o;

		// Generic checks, not domain specific
		ValidationUtils.rejectIfEmptyOrWhitespace(errors, "password", "page.selfservice.changePassword.error.required");
		ValidationUtils.rejectIfEmptyOrWhitespace(errors, "confirmPassword", "page.selfservice.changePassword.error.required");

		if (!Objects.equals(form.getPassword(), form.getConfirmPassword())) {
			errors.rejectValue("confirmPassword", "page.selfservice.changePassword.error.match");
		}

		// before we can check if the password fills all requirements we need to find which person is associated with the login so we can get the password settings
		Person person = sessionHelper.getPerson();
		Domain domain = person.getDomain();
		PasswordSetting settings = passwordSettingService.getSettings(domain);

		// Domain specific checks
		boolean wrongPassword = false;

		if (form.getPassword().length() < settings.getMinLength()) {
			wrongPassword = true;
		}

		if (settings.isBothCapitalAndSmallLetters()) {
			if (!Pattern.compile("[A-ZÆØÅ]").matcher(form.getPassword()).find()) {
				wrongPassword = true;
			}
			else if (!Pattern.compile("[a-zæøå]").matcher(form.getPassword()).find()) {
				wrongPassword = true;
			}
		}

		if (settings.isRequireDigits()) {
			if (!Pattern.compile("\\d").matcher(form.getPassword()).find()) {
				wrongPassword = true;
			}
		}

		if (settings.isRequireSpecialCharacters()) {
			if (!Pattern.compile("[^\\wæøå\\d]", Pattern.CASE_INSENSITIVE).matcher(form.getPassword()).find()) {
				wrongPassword = true;
			}
		}

		if (settings.isDisallowDanishCharacters()) {
			if (Pattern.compile("[æøåÆØÅ]").matcher(form.getPassword()).find()) {
				wrongPassword = true;
			}
		}

		if (wrongPassword) {
			errors.rejectValue("password", "page.selfservice.changePassword.error.rules");
		}
	}
}