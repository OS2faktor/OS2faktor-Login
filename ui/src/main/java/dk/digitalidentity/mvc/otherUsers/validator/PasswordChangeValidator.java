package dk.digitalidentity.mvc.otherUsers.validator;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.service.PasswordValidationService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.enums.ChangePasswordResult;
import dk.digitalidentity.mvc.students.dto.PasswordChangeForm;

@Component
public class PasswordChangeValidator implements Validator {

	@Autowired
	private PasswordValidationService passwordValidationService;

	@Autowired
	private PersonService personService;

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
		ValidationUtils.rejectIfEmptyOrWhitespace(errors, "personId", "page.selfservice.changePassword.error.required");

		if (!Objects.equals(form.getPassword(), form.getConfirmPassword())) {
			errors.rejectValue("confirmPassword", "page.selfservice.changePassword.error.match");
		}

		Person person = personService.getById(form.getPersonId());
		ChangePasswordResult validPassword = passwordValidationService.validatePasswordRules(person, form.getPassword(), true, false);

		switch (validPassword) {
			case BAD_PASSWORD:
				errors.rejectValue("password", "page.selfservice.changePassword.error.simple");
				break;
			case LEAKED_PASSWORD:
				errors.rejectValue("password", "page.selfservice.changePassword.error.leak");
				break;
			case CONTAINS_NAME:
			case DANISH_CHARACTERS_NOT_ALLOWED:
			case NO_DIGITS:
			case NO_LOWERCASE:
			case NO_SPECIAL_CHARACTERS:
			case NO_UPPERCASE:
			case NOT_COMPLEX:
			case OLD_PASSWORD:
			case TECHNICAL_MISSING_PERSON:
			case TOO_LONG:
			case TOO_SHORT:
			case WRONG_SPECIAL_CHARACTERS:
				errors.rejectValue("password", "page.selfservice.changePassword.error.rules");
				break;
			case OK:
				break;
		}
	}
}