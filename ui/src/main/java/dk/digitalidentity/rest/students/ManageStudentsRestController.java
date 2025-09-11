package dk.digitalidentity.rest.students;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.model.ADPasswordResponse;
import dk.digitalidentity.common.service.model.ADPasswordResponse.ADPasswordStatus;
import dk.digitalidentity.security.RequireChangePasswordOnOthersRole;
import dk.digitalidentity.security.SecurityUtil;

@RequireChangePasswordOnOthersRole
@RestController
public class ManageStudentsRestController {

	@Autowired
	private PersonService personService;

	@Autowired
	private SecurityUtil securityUtil;

	@Autowired
	private CommonConfiguration commonConfiguration;
	
	@PostMapping("/rest/student/{id}/unlockAccount")
	public ResponseEntity<String> unlockAccount(@PathVariable("id") long id) {
		Person personToBeEdited = personService.getById(id);
		if (personToBeEdited == null) {
			return ResponseEntity.badRequest().body("Fejl! Brugeren findes ikke");
		}

		if (!allowedAccess(personToBeEdited)) {
			return ResponseEntity.badRequest().body("Fejl! Det er ikke tilladt at låse denne brugers konto op.");
		}

		ADPasswordStatus adPasswordStatus = personService.unlockAccount(personToBeEdited, securityUtil.getPerson());
		if (ADPasswordResponse.isCritical(adPasswordStatus)) {
			return ResponseEntity.badRequest().body("Kontoen kunne ikke låses op.");
		}

		return ResponseEntity.ok().build();
	}

	private boolean allowedAccess(Person personToBeEdited) {
		if (!commonConfiguration.getStilStudent().isEnabled()) {
			return false;
		}

		Person loggedInPerson = personService.getById(securityUtil.getPersonId());
		if (loggedInPerson == null) {
			return false;
		}

		if (personToBeEdited == null) {
			return false;
		}

		if (personToBeEdited.isNsisAllowed()) {
			return false;
		}

		if (personService.getStudentsThatPasswordCanBeChangedOnByPerson(loggedInPerson, null).stream().anyMatch(p -> p.getUuid().equals(personToBeEdited.getUuid()))) {
			return true;
		}

		return false;
	}
	
}
