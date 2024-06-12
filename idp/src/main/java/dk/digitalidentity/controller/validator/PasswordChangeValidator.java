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
import dk.digitalidentity.service.PasswordService;
import dk.digitalidentity.service.SessionHelper;

@Component
public class PasswordChangeValidator implements Validator {

	@Autowired
	private PasswordSettingService passwordSettingService;

	@Autowired
	private SessionHelper sessionHelper;
	
	@Autowired
	private PasswordService passwordService;

	@Override
	public boolean supports(Class<?> aClass) {
		return (PasswordChangeForm.class.isAssignableFrom(aClass));
	}

	@Override
	public void validate(Object o, Errors errors) {
		PasswordChangeForm form = (PasswordChangeForm) o;
		Person person = sessionHelper.getPerson();
		
		// this is not the best way to do it, but we are talking about someone posting against an endpoint without being logged in, so I guess
		// the actual error does not matter that much :)
		if (person == null) {
			errors.rejectValue("oldPassword", "page.selfservice.changePassword.error.required");
			return;
		}
		
		if (!sessionHelper.isAuthenticatedWithNemIdOrMitId()) {
			ValidationUtils.rejectIfEmptyOrWhitespace(errors, "oldPassword", "page.selfservice.changePassword.error.required");

			if (!passwordService.validatePassword(form.getOldPassword(), person).isNoErrors()) {
				errors.rejectValue("oldPassword", "page.selfservice.changePassword.error.oldPasswordWrong");
			}
		}
		
		// Generic checks, not domain specific
		ValidationUtils.rejectIfEmptyOrWhitespace(errors, "password", "page.selfservice.changePassword.error.required");
		ValidationUtils.rejectIfEmptyOrWhitespace(errors, "confirmPassword", "page.selfservice.changePassword.error.required");

		if (!Objects.equals(form.getPassword(), form.getConfirmPassword())) {
			errors.rejectValue("confirmPassword", "page.selfservice.changePassword.error.match");
		}

		ChangePasswordResult validPassword = passwordSettingService.validatePasswordRules(person, form.getPassword(), true);

		if (validPassword.equals(ChangePasswordResult.BAD_PASSWORD)) {
			errors.rejectValue("password", "page.selfservice.changePassword.error.simple");
			sessionHelper.setPasswordChangeFailureReason(validPassword);
		}
		else if (!validPassword.equals(ChangePasswordResult.OK)) {
			switch (validPassword) {
				case CONTAINS_NAME:
					errors.rejectValue("password", "page.selfservice.changePassword.error.rules.containsName");
					sessionHelper.setPasswordChangeFailureReason(validPassword);
					break;
				case DANISH_CHARACTERS_NOT_ALLOWED:
					errors.rejectValue("password", "page.selfservice.changePassword.error.rules.danishNotAllowed");
					sessionHelper.setPasswordChangeFailureReason(validPassword);
					break;
				case NO_DIGITS:
					errors.rejectValue("password", "page.selfservice.changePassword.error.rules.noDigits");
					sessionHelper.setPasswordChangeFailureReason(validPassword);
					break;
				case NO_LOWERCASE:
					errors.rejectValue("password", "page.selfservice.changePassword.error.rules.noLowercase");
					sessionHelper.setPasswordChangeFailureReason(validPassword);
					break;
				case NO_SPECIAL_CHARACTERS:
					errors.rejectValue("password", "page.selfservice.changePassword.error.rules.noSpecialCharacters");
					sessionHelper.setPasswordChangeFailureReason(validPassword);
					break;
				case NO_UPPERCASE:
					errors.rejectValue("password", "page.selfservice.changePassword.error.rules.noUppercase");
					sessionHelper.setPasswordChangeFailureReason(validPassword);
					break;
				case NOT_COMPLEX:
					errors.rejectValue("password", "page.selfservice.changePassword.error.rules.notComplex");
					sessionHelper.setPasswordChangeFailureReason(validPassword);
					break;
				case OLD_PASSWORD:
					errors.rejectValue("password", "page.selfservice.changePassword.error.rules.oldPassword");
					sessionHelper.setPasswordChangeFailureReason(validPassword);
					break;
				case TOO_LONG:
					errors.rejectValue("password", "page.selfservice.changePassword.error.rules.tooLong");
					sessionHelper.setPasswordChangeFailureReason(validPassword);
					break;
				case TOO_SHORT:
					errors.rejectValue("password", "page.selfservice.changePassword.error.rules.tooShort");
					sessionHelper.setPasswordChangeFailureReason(validPassword);
					break;
				case WRONG_SPECIAL_CHARACTERS:
					errors.rejectValue("password", "page.selfservice.changePassword.error.rules.wrongSpecialCharacters");
					sessionHelper.setPasswordChangeFailureReason(validPassword);
					break;
				case OK:
					// does not happen - we just keep this here to not trigger an IDE warning about missing case handling
					break;
				case BAD_PASSWORD:
				case TECHNICAL_MISSING_PERSON:
					errors.rejectValue("password", "page.selfservice.changePassword.error.rules");
					sessionHelper.setPasswordChangeFailureReason(validPassword);
					break;
                case LEAKED_PASSWORD:
					errors.rejectValue("password", "page.selfservice.changePassword.error.leaked");
					sessionHelper.setPasswordChangeFailureReason(validPassword);
					break;
            }
		}
	}
}