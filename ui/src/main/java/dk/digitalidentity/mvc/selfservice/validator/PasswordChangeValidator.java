package dk.digitalidentity.mvc.selfservice.validator;

import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

import dk.digitalidentity.common.dao.model.PasswordSetting;
import dk.digitalidentity.common.service.PasswordSettingService;
import dk.digitalidentity.mvc.selfservice.dto.PasswordChangeForm;

@Component
public class PasswordChangeValidator implements Validator {

	@Autowired
	private PasswordSettingService passwordSettingService;

	@Override
	public boolean supports(Class<?> aClass) {
		return (PasswordChangeForm.class.isAssignableFrom(aClass));
	}

	@Override
	public void validate(Object o, Errors errors) {
		PasswordChangeForm form = (PasswordChangeForm) o;
		PasswordSetting settings = passwordSettingService.getSettings();

		ValidationUtils.rejectIfEmptyOrWhitespace(errors, "password", "page.selfservice.changePassword.error.required");
		ValidationUtils.rejectIfEmptyOrWhitespace(errors, "confirmPassword", "page.selfservice.changePassword.error.required");

		if (!Objects.equals(form.getPassword(), form.getConfirmPassword())) {
			errors.rejectValue("confirmPassword", "page.selfservice.changePassword.error.match");
		}

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

		if (wrongPassword) {
			errors.rejectValue("password", "page.selfservice.changePassword.error.rules");
		}
	}
}