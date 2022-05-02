package dk.digitalidentity.controller.validator;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.service.PasswordSettingService;
import dk.digitalidentity.common.service.enums.ChangePasswordResult;
import dk.digitalidentity.controller.dto.PasswordChangeForm;
import dk.digitalidentity.service.SessionHelper;

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

		Person person = sessionHelper.getPerson();
		ChangePasswordResult validPassword = passwordSettingService.validatePasswordRules(person, form.getPassword());

		if (validPassword.equals(ChangePasswordResult.BAD_PASSWORD)) {
			errors.rejectValue("password", "page.selfservice.changePassword.error.simple");
			sessionHelper.setPasswordChangeFailureReason(validPassword);
		}
		else if (!validPassword.equals(ChangePasswordResult.OK)) {
			errors.rejectValue("password", "page.selfservice.changePassword.error.rules");
			sessionHelper.setPasswordChangeFailureReason(validPassword);
		}
	}
}