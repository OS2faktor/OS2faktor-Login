package dk.digitalidentity.rest.admin;

import java.util.Date;

import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.common.dao.model.LocalRegisteredMfaClient;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.LocalRegisteredMfaClientService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.mfa.model.ClientType;
import dk.digitalidentity.mvc.admin.dto.ActivationDTO;
import dk.digitalidentity.security.RequireAnyAdminRole;
import dk.digitalidentity.security.SecurityUtil;
import dk.digitalidentity.service.MFAManagementService;
import dk.digitalidentity.service.MFAManagementService.RobotMfaRegistrationRequest;
import dk.digitalidentity.service.MFAManagementService.RobotMfaRegistrationResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequireAnyAdminRole
@RequiredArgsConstructor
@RestController
@Slf4j
public class RobotMfaRestController {
	private final LocalRegisteredMfaClientService localRegisteredMfaClientService;  
	private final MFAManagementService mfaManagementService;
	private final PersonService personService;
	private final AuditLogger auditLogger;
	private final SecurityUtil securityUtil;
	private final ResourceBundleMessageSource resourceBundle;

	@PostMapping("/admin/robotMFA/register/{robotId}")
	public ResponseEntity<String> register(HttpServletRequest request, @PathVariable long robotId) {
		Person admin = personService.getById(securityUtil.getPersonId());
		if (admin == null) {
			log.warn("Unknown admin: " + securityUtil.getPersonId());
			return ResponseEntity.badRequest().body("Ikke logget ind som administrator");
		}

		Person person = personService.getById(robotId);
		if (person == null) {
			log.warn("No person with id " + robotId);
			return ResponseEntity.badRequest().body("Denne person findes ikke");
		}
		
		if (!person.isRobot()) {
			log.warn("Person is not a robot :" + person.getId());
			return ResponseEntity.badRequest().body("Denne person er ikke en robot");
		}
		
		if (localRegisteredMfaClientService.getByCpr(person.getCpr()).size() > 0) {
			return ResponseEntity.badRequest().body("Robotten har allerede en MFA klient");
		}
		
		RobotMfaRegistrationResponse registrationResponse = mfaManagementService.robotRegistionRequester(new RobotMfaRegistrationRequest("RobotMFA"));
		if (registrationResponse == null) {
			return ResponseEntity.internalServerError().body("Der opstod en teknisk ufejl under registreringen");
		}

		LocalRegisteredMfaClient client = new LocalRegisteredMfaClient();
		client.setDeviceId(registrationResponse.deviceId());
		client.setName("RobotMFA");
		client.setType(ClientType.TOTP);
		client.setNsisLevel(NSISLevel.NONE);
		client.setCpr(person.getCpr());
		client.setAssociatedUserTimestamp(new Date());
		localRegisteredMfaClientService.save(client);
		
		// add useful logging information
		ActivationDTO activationDTO = new ActivationDTO();
		activationDTO.setNsisLevel(client.getNsisLevel());
		activationDTO.setName(client.getName());
		activationDTO.setType(client.getType());
		activationDTO.setDeviceId(client.getDeviceId());
		
		auditLogger.manualMfaAssociation(activationDTO.toIdentificationDetails(resourceBundle), person, admin);

		return ResponseEntity.ok().body(registrationResponse.secret());
	}
}