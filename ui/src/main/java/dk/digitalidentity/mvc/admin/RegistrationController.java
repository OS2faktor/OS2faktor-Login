package dk.digitalidentity.mvc.admin;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import dk.digitalidentity.common.dao.model.LocalRegisteredMfaClient;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.LocalRegisteredMfaClientService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.mfa.MFAService;
import dk.digitalidentity.common.service.mfa.model.MfaClient;
import dk.digitalidentity.mvc.admin.dto.ActivationDTO;
import dk.digitalidentity.mvc.admin.dto.IdentificationType;
import dk.digitalidentity.security.RequireRegistrant;
import dk.digitalidentity.security.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequireRegistrant
@Controller
public class RegistrationController {

	@Autowired
	private PersonService personService;
	
	@Autowired
	private AuditLogger auditLogger;
	
	@Autowired
	private SecurityUtil securityUtil;
		
	@Autowired
	private LocalRegisteredMfaClientService localRegisteredMfaClientService;
	
	@Autowired
	private MFAService mfaService;
	
	@Autowired
	private ResourceBundleMessageSource resourceBundle;
	
	@GetMapping("/admin/registration/mfa")
	public String usersMFA(Model model, @RequestParam(value = "result", required = false, defaultValue = "false") boolean registered) {
		if (registered) {
			model.addAttribute("flashMessage", "Registrering afsluttet");
		}
		return "registration/mfa/mfa";
	}
	
	@GetMapping("/admin/registration/mfa/{id}/search")
	public String searchMFA(Model model, @PathVariable Long id) {
		Person person = personService.getById(id);
		if (person == null) {
			return "redirect:/admin/registration";
		}

		ActivationDTO dto = new ActivationDTO();
		dto.setIdentificationType(IdentificationType.PASSPORT);
		dto.setPersonId(id);

		model.addAttribute("activationForm", dto);
		
		return "registration/mfa/search";
	}
	
	@PostMapping("/admin/registration/mfa/associate")
	public ResponseEntity<?> manualMfaAssociation(@RequestBody ActivationDTO activationDTO, BindingResult bindingResult, HttpServletRequest httpServletRequest) {
		Person admin = personService.getById(securityUtil.getPersonId());
		if (admin == null) {
			log.warn("Unknown admin: " + securityUtil.getPersonId());
			return ResponseEntity.notFound().build();
		}

		Person person = personService.getById(activationDTO.getPersonId());
		if (person == null) {
			log.warn("Unknown person: " + activationDTO.getPersonId());
			return ResponseEntity.notFound().build();
		}

		MfaClient mfaClient = mfaService.getClient(activationDTO.getDeviceId());
		if (mfaClient == null) {
			log.warn("Unknown MFA client: " + activationDTO.getDeviceId());
			return ResponseEntity.notFound().build();
		}

		LocalRegisteredMfaClient localClient = localRegisteredMfaClientService.getByDeviceId(activationDTO.getDeviceId());
		if (localClient != null) {
			log.warn("MFA client already registered to someone else: " + activationDTO.getDeviceId());
			return ResponseEntity.notFound().build();
		}

		LocalRegisteredMfaClient client = new LocalRegisteredMfaClient();
		client.setDeviceId(mfaClient.getDeviceId());
		client.setName(mfaClient.getName());
		client.setType(mfaClient.getType());
		client.setNsisLevel(NSISLevel.NONE);
		client.setCpr(person.getCpr());
		client.setAssociatedUserTimestamp(new Date());
		
		localRegisteredMfaClientService.save(client);
		
		// add useful logging information
		activationDTO.setNsisLevel(NSISLevel.NONE);
		activationDTO.setName(mfaClient.getName());
		activationDTO.setType(mfaClient.getType());
		
		auditLogger.manualMfaAssociation(activationDTO.toIdentificationDetails(resourceBundle), person, admin);
		
		return ResponseEntity.ok("");
	}
}
