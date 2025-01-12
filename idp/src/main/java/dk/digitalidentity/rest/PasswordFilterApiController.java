package dk.digitalidentity.rest;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.PasswordValidationService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.enums.ChangePasswordResult;
import dk.digitalidentity.filter.PasswordValidationApiFilter;
import dk.digitalidentity.rest.model.PasswordFilterValidationDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
public class PasswordFilterApiController {
    private final AuditLogger auditLogger;
    private final PersonService personService;
	private final PasswordValidationService passwordValidationService;

	/**
	 * Processes the PasswordFilters password validation request and returns an HTTP response.
	 *
	 * <p>This method may return the following HTTP response codes based on the outcome of the request:
	 * <ul>
	 *   <li><b>200 OK:</b> The password was successfully validated</li>
	 *   <li><b>400 Bad Request:</b> The request was malformed or contains invalid parameters.</li>
	 *   <li><b>409 Conflict:</b> The password did not pass validation</li>
	 *   <li><b>500 Internal Server Error:</b> The server encountered an unexpected condition that prevented it from
	 *   fulfilling the request.</li>
	 * </ul>
	 *
	 * @return the HTTP response containing the result of the operation
	 */
	@PostMapping("/api/password/filter/v1/validate")
	@ResponseBody
	public ResponseEntity<String> validatePassword(@RequestBody PasswordFilterValidationDTO body) {
		Domain domain = PasswordValidationApiFilter.domainHolder.get();
		if (domain == null) {
			log.info("No domain matched incoming client request");
			return ResponseEntity.badRequest().build();
		}

		String username = body.getAccountName();
		String password = body.getPassword();

		// Validate incoming data
		if (!StringUtils.hasLength(username) || !StringUtils.hasLength(password)) {
			log.warn("Missing username/password parameters");
			return ResponseEntity.badRequest().build();
		}

		// Get Domains to search for person
		List<Domain> domains = getDomainList(domain);

		// Get Person
		List<Person> persons = personService.getBySamaccountNameAndDomains(username, domains);
		if (persons.size() != 1) {
			log.warn("Found more than one matching user");
			return ResponseEntity.badRequest().build();
		}

		// Check password if not ok, log special Auditlog specifying PasswordFilter Validation failed.
		Person person = persons.get(0);
		ChangePasswordResult result = passwordValidationService.validatePasswordRules(person, password, false);
		switch (result) {
			case OK:
				return ResponseEntity.ok().build();
				
			// add the cases here, to make sure we get a compile warning on new enum values
			case TECHNICAL_MISSING_PERSON:
			case TOO_SHORT:
			case TOO_LONG:
			case NOT_COMPLEX:
			case NO_LOWERCASE:
			case NO_UPPERCASE:
			case NO_DIGITS:
			case NO_SPECIAL_CHARACTERS:
			case DANISH_CHARACTERS_NOT_ALLOWED:
			case CONTAINS_NAME:
			case OLD_PASSWORD:
			case BAD_PASSWORD:
			case WRONG_SPECIAL_CHARACTERS:
			case LEAKED_PASSWORD:
				break;
		}
		
		auditLogger.passwordFilterValidationFailed(person, result.getMessage());

		return ResponseEntity.status(HttpStatus.CONFLICT).build();
	}

	private static List<Domain> getDomainList(Domain domain) {
		if (domain.getParent() != null) {
			domain = domain.getParent();
		}

		List<Domain> domains = new ArrayList<>();
		domains.add(domain);
		if (domain.getChildDomains() != null) {
			for (Domain d : domain.getChildDomains()) {
				domains.add(d);
			}
		}

		return domains;
	}
}
