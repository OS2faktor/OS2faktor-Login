package dk.digitalidentity.mvc.admin;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.util.HtmlUtils;

import dk.digitalidentity.common.dao.model.LocalRegisteredMfaClient;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.mfa.MFAService;
import dk.digitalidentity.common.service.mfa.model.MfaClient;
import dk.digitalidentity.common.service.LocalRegisteredMfaClientService;
import dk.digitalidentity.mvc.admin.dto.ActivationDTO;
import dk.digitalidentity.mvc.admin.dto.IdentificationType;
import dk.digitalidentity.mvc.admin.dto.UsernamePasswordDTO;
import dk.digitalidentity.security.RequireRegistrant;
import dk.digitalidentity.security.SecurityUtil;
import dk.digitalidentity.service.EboksService;
import dk.digitalidentity.util.UsernameAndPasswordHelper;
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
	private EboksService eboksService;
	
	@Autowired
	private UsernameAndPasswordHelper usernameAndPasswordHelper;
	
	@Autowired
	private LocalRegisteredMfaClientService localRegisteredMfaClientService;
	
	@Autowired
	private MFAService mfaService;

	@GetMapping("/admin/registration")
	public String users(Model model) {
		return "registration/registration";
	}

	@GetMapping("/admin/registration/activation/{id}")
	public String activation(Model model, @PathVariable Long id) {
		Person person = personService.getById(id);
		if (person == null) {
			return "redirect:/admin/registration";
		}
		
		if (!canBeActivated(person)) {
			log.warn("Attempting to activate person that cannot be activated! Person.id = " + person.getId());
			return "redirect:/admin/registration";
		}

		ActivationDTO dto = new ActivationDTO();
		dto.setIdentificationType(IdentificationType.PASSPORT);
		dto.setNsisLevel(NSISLevel.SUBSTANTIAL);
		dto.setPersonId(id);
		
		model.addAttribute("person", person);
		model.addAttribute("activationForm", dto);

		return "registration/activation";
	}

	@PostMapping("/admin/registration/activation/step1")
	public ResponseEntity<?> manualActivation(@Valid @RequestBody ActivationDTO activationDTO, BindingResult bindingResult, HttpServletRequest httpServletRequest) {
		if (bindingResult.hasErrors()) {
			return ResponseEntity.badRequest().body("Ikke alle felter korrekt udfyldt");
		}

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

		if (!canBeActivated(person)) {
			log.warn("Attempting to activate person that cannot be activated! Person.id = " + person.getId());
			return ResponseEntity.badRequest().body("Den valgte person kan ikke få aktiveret sin erhvervsidentitet");
		}
		
		String password = usernameAndPasswordHelper.generatePassword(person.getDomain());
		String userId = usernameAndPasswordHelper.getUserId(person);

		boolean eboksSuccess = eboksService.sendMessage(person.getCpr(),
				"<h4>Erhvervsidentitet aktiveret<h4>",
				"<p>Din arbejdgiver har aktiveret din erhvervsidentitet, som du kan anvende med nedenstående brugernavn og kodeord.</p><br/>Brugernavn: " + userId + "<br/>Kodeord: " + HtmlUtils.htmlEscape(password, "UTF-8"));

		if (eboksSuccess == false) {
			return ResponseEntity.status(500).body("eboks");
		}

		boolean success = activateNSISAccount(person, activationDTO.getNsisLevel(), userId, password);
		if (success) {
			auditLogger.manualActivation(activationDTO, person, admin, false);
			personService.save(person);

			return ResponseEntity.ok().build();
		}

		log.warn("Failed to activate NSIS account for person ID: " + person.getId());

		return ResponseEntity.status(500).body("internal");
	}

	@PostMapping("/admin/registration/activation/step2")
	public ResponseEntity<?> manualActivationStep2(@Valid @RequestBody ActivationDTO activationDTO, BindingResult bindingResult, HttpServletRequest httpServletRequest) {
		if (bindingResult.hasErrors()) {
			return ResponseEntity.badRequest().body("Ikke alle felter korrekt udfyldt");
		}

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

		if (!canBeActivated(person)) {
			log.warn("Attempting to activate person that cannot be activated! Person.id = " + person.getId());
			return ResponseEntity.badRequest().body("Den valgte person kan ikke få aktiveret sin erhvervsidentitet");
		}

		// freshly generated, we threw away the other set
		String password = usernameAndPasswordHelper.generatePassword(person.getDomain());
		String userId = usernameAndPasswordHelper.getUserId(person);

		boolean success = activateNSISAccount(person, activationDTO.getNsisLevel(), userId, password);
		if (success) {
			auditLogger.manualActivation(activationDTO, person, admin, true);
			personService.save(person);

			return ResponseEntity.ok(new UsernamePasswordDTO(userId, password));
		}
		
		log.warn("Failed to activate NSIS account for person ID: " + person.getId());

		return ResponseEntity.status(500).body("internal");
	}
	
	@GetMapping("/admin/registration/mfa")
	public String usersMFA(Model model) {
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
		client.setNsisLevel(NSISLevel.SUBSTANTIAL);
		client.setCpr(person.getCpr());
		
		localRegisteredMfaClientService.save(client);
		
		// add useful logging information
		activationDTO.setNsisLevel(NSISLevel.SUBSTANTIAL);
		activationDTO.setName(mfaClient.getName());
		activationDTO.setType(mfaClient.getType());
		
		auditLogger.manualMfaAssociation(activationDTO, person, admin);
		
		return ResponseEntity.ok(client);
	}

	private boolean activateNSISAccount(Person person, NSISLevel level, String userId, String password) {
		person.setNsisLevel(level);
		person.setUserId(userId);

		try {
			// change password, bypassing validation and AD replication
			return personService.changePassword(person, password, true, true);
		}
		catch (Exception ex) {
			// this can only fail if there are programming errors, e.g. bad algorithms
			log.warn("Error occured while trying to change password", ex);
			return false;
		}
	}

	private boolean canBeActivated(Person person) {
		if (person.isNsisAllowed() == false ||
			person.hasNSISUser() == true ||
			person.isApprovedConditions() == false) {
			
			return false;
		}

		return true;
	}
}
