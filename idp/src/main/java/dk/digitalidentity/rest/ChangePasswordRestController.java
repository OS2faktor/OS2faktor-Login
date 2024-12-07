package dk.digitalidentity.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.service.PasswordValidationService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.enums.ChangePasswordResult;
import dk.digitalidentity.rest.model.SelectUserDTO;
import dk.digitalidentity.rest.model.UserIdPasswordDTO;
import dk.digitalidentity.service.SessionHelper;

@RestController
public class ChangePasswordRestController {

	@Autowired
	private PasswordValidationService passwordValidationService;

	@Autowired
	private SessionHelper sessionHelper;
	
	@Autowired
	private PersonService personService;

	@PostMapping("/sso/saml/rest/validpassword")
	public ResponseEntity<?> validPassword(Model model, @RequestBody String password) {
		Person person = sessionHelper.getPerson();
		if (person == null) {
			return new ResponseEntity<>("Ikke logget ind", HttpStatus.BAD_REQUEST);
		}

		ChangePasswordResult result = passwordValidationService.validatePasswordRulesWithoutSlowValidationRules(person, password);

		if (!result.equals(ChangePasswordResult.OK)) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	@PostMapping("/sso/saml/rest/validpassword/nonflow")
	public ResponseEntity<?> nonFlowValidatePassword(@RequestBody UserIdPasswordDTO userIdPasswordDTO) {
		Person person = personService.getById(userIdPasswordDTO.getUserId());
		if (person == null) {
			return new ResponseEntity<>(HttpStatus.OK);
		}

		// we just validate against normal password requirements, if a password is invalid due to password history, we wait to tell them until they post (and count up bad password)
		ChangePasswordResult result = passwordValidationService.validatePasswordRulesWithoutSlowValidationRules(person, userIdPasswordDTO.getPassword());
		
		if (!result.equals(ChangePasswordResult.OK)) {
			return new ResponseEntity<>(getResultText(result),HttpStatus.BAD_REQUEST);
		}
		
		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	@GetMapping("/sso/saml/rest/getusers/{username}")
	public ResponseEntity<?> getUsersByUsername(Model model, @PathVariable String username){
		//Doing no checks of any kind as all usernames are "valid" to prevent guessing/checking if usernames exist.ss
		List<SelectUserDTO> selectUserDTOS = new ArrayList<>();
		List<Person> allWithUsername = personService.getBySamaccountName(username);
		//Mapping to a DTO so we don't transfer cpr etc. to frontend
		for (Person person : allWithUsername) {
			selectUserDTOS.add(new SelectUserDTO(person.getId(), person.getSamaccountName(), person.getName(), person.getDomain()));
		}
		
		return new ResponseEntity<>(selectUserDTOS, HttpStatus.OK);
	}

	private String getResultText(ChangePasswordResult validPassword) {
		ResourceBundle bundle = ResourceBundle.getBundle("messages");

		switch (validPassword) {
			case CONTAINS_NAME:
				return bundle.getString("page.selfservice.changePassword.error.rules.containsName");
			case DANISH_CHARACTERS_NOT_ALLOWED:
				return bundle.getString("page.selfservice.changePassword.error.rules.danishNotAllowed");
			case NO_DIGITS:
				return bundle.getString("page.selfservice.changePassword.error.rules.noDigits");
			case NO_LOWERCASE:
				return bundle.getString("page.selfservice.changePassword.error.rules.noLowercase");
			case NO_SPECIAL_CHARACTERS:
				return bundle.getString("page.selfservice.changePassword.error.rules.noSpecialCharacters");
			case NO_UPPERCASE:
				return bundle.getString("page.selfservice.changePassword.error.rules.noUppercase");
			case NOT_COMPLEX:
				return bundle.getString("page.selfservice.changePassword.error.rules.notComplex");
			case OLD_PASSWORD:
				return bundle.getString("page.selfservice.changePassword.error.rules.oldPassword");
			case TOO_LONG:
				return bundle.getString("page.selfservice.changePassword.error.rules.tooLong");
			case TOO_SHORT:
				return bundle.getString("page.selfservice.changePassword.error.rules.tooShort");
			case WRONG_SPECIAL_CHARACTERS:
				return bundle.getString("page.selfservice.changePassword.error.rules.wrongSpecialCharacters");
			case LEAKED_PASSWORD:
				return bundle.getString("page.selfservice.changePassword.error.rules.wrongSpecialCharacters");
			case OK:
				// does not happen - we just keep this here to not trigger an IDE warning about missing case handling
				break;
			case TECHNICAL_MISSING_PERSON:
			case BAD_PASSWORD:
				break;
		}
		
		return "Kodeord opfylder ikke regler ";
	}
}
