package dk.digitalidentity.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.service.PasswordSettingService;
import dk.digitalidentity.common.service.enums.ChangePasswordResult;
import dk.digitalidentity.service.SessionHelper;

@RestController
public class ChangePasswordRestController {

	@Autowired
	private PasswordSettingService passwordSettingService;

	@Autowired
	private SessionHelper sessionHelper;

	@PostMapping("/sso/saml/rest/validpassword")
	public ResponseEntity<?> validPassword(Model model, @RequestBody String password) {
		Person person = sessionHelper.getPerson();
		if (person == null) {
			return new ResponseEntity<>("Ikke logget ind", HttpStatus.BAD_REQUEST);
		}

		ChangePasswordResult result = passwordSettingService.validatePasswordRules(person, password);

		if (!result.equals(ChangePasswordResult.OK)) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}
}
