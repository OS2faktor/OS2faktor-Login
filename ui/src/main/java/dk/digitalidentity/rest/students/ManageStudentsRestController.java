package dk.digitalidentity.rest.students;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.SchoolClass;
import dk.digitalidentity.common.dao.model.enums.SchoolRoleValue;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.SchoolClassService;
import dk.digitalidentity.common.service.StudentPasswordProposalService;
import dk.digitalidentity.common.service.model.ADPasswordResponse;
import dk.digitalidentity.common.service.model.ADPasswordResponse.ADPasswordStatus;
import dk.digitalidentity.security.RequireChangePasswordOnOthersRole;
import dk.digitalidentity.security.SecurityUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequireChangePasswordOnOthersRole
@RestController
public class ManageStudentsRestController {

	@Autowired
	private PersonService personService;

	@Autowired
	private SecurityUtil securityUtil;

	@Autowired
	private CommonConfiguration commonConfiguration;

	@Autowired
	private StudentPasswordProposalService studentPasswordProposalService;

	@Autowired
	private SchoolClassService schoolClassService;

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

	@GetMapping("/rest/student/{id}/passwordproposal")
	public ResponseEntity<String> getPasswordProposal(@PathVariable("id") long id) {
		Person student = personService.getById(id);
		if (student == null) {
			return ResponseEntity.badRequest().body("Fejl! Brugeren findes ikke");
		}

		return ResponseEntity.ok().body(studentPasswordProposalService.getPasswordProposal(student, false));
	}

	@PostMapping("/rest/student/bulkchangepassword/{schoolClassId}")
	public ResponseEntity<?> bulkChangePassword(@PathVariable long schoolClassId, @RequestBody List<Long> studentIds) {
		if (studentIds == null || studentIds.isEmpty()) {
			return ResponseEntity.badRequest().body("Ingen elever valgt");
		}

		Person loggedInPerson = personService.getById(securityUtil.getPersonId());
		if (loggedInPerson == null) {
			return ResponseEntity.badRequest().body("Fejl! Bruger ikke fundet");
		}

		SchoolClass schoolClass = schoolClassService.getById(schoolClassId);
		if (schoolClass == null) {
			return ResponseEntity.badRequest().body("Fejl! Klasse ikke fundet");
		}

		if (!schoolClassService.isBulkChangeAllowedForClass(schoolClass, commonConfiguration.getStilStudent().getBulkChangePasswordOnLevelAndBelow())) {
			return ResponseEntity.badRequest().body("Fejl! Der kan ikke skiftes kodeord på flere elever på én gang i denne klasse");
		}

		List<Person> allowedStudents = personService.getStudentsThatPasswordCanBeChangedOnByPerson(loggedInPerson, null);

		Map<Long, Person> allowedStudentsById = allowedStudents.stream()
				.collect(Collectors.toMap(Person::getId, p -> p));

		List<Long> classStudentIds = schoolClass.getRoleMappings().stream()
				.filter(r -> r.getSchoolRole().getRole().equals(SchoolRoleValue.STUDENT))
				.map(r -> r.getSchoolRole().getPerson().getId())
				.collect(Collectors.toList());

		int successCount = 0;
		List<String> failedUsernames = new ArrayList<>();

		for (Long studentId : studentIds) {
			if (!classStudentIds.contains(studentId)) {
				failedUsernames.add("ID: " + studentId);
				continue;
			}

			Person student = allowedStudentsById.get(studentId);

			if (student == null) {
				failedUsernames.add("ID: " + studentId);
				continue;
			}

			String newPassword = studentPasswordProposalService.getPasswordProposal(student, true);
			if (newPassword == null) {
				failedUsernames.add(student.getSamaccountName());
				continue;
			}

			try {
				ADPasswordResponse.ADPasswordStatus adPasswordStatus = personService.changePasswordByAdmin(student, newPassword, loggedInPerson, false);

				if (ADPasswordResponse.isCritical(adPasswordStatus)) {
					failedUsernames.add(student.getSamaccountName());
				} else {
					successCount++;
				}
			}
			catch (NoSuchPaddingException | InvalidKeyException | NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException | UnsupportedEncodingException | InvalidAlgorithmParameterException e) {
				log.error("Exception while trying to bulk change password on another user", e);
			}
		}

		if (!failedUsernames.isEmpty()) {
			String failedList = String.join(", ", failedUsernames);
			return ResponseEntity.ok()
					.body("Kodeord skiftet på " + successCount + " elev(er). Fejlede for: " + failedList);
		}

		return ResponseEntity.ok()
				.body("Kodeord skiftet på " + successCount + " elev(er)");
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
