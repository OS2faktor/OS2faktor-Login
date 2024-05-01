package dk.digitalidentity.mvc.admin;

import java.util.Date;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.HtmlUtils;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.LocalRegisteredMfaClient;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.CprService;
import dk.digitalidentity.common.service.LocalRegisteredMfaClientService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.mfa.MFAService;
import dk.digitalidentity.common.service.mfa.model.MfaClient;
import dk.digitalidentity.mvc.admin.dto.ActivationDTO;
import dk.digitalidentity.mvc.admin.dto.IdentificationType;
import dk.digitalidentity.mvc.admin.dto.UsernamePasswordDTO;
import dk.digitalidentity.security.RequireRegistrant;
import dk.digitalidentity.security.SecurityUtil;
import dk.digitalidentity.service.EboksService;
import dk.digitalidentity.service.EboksService.SendStatus;
import dk.digitalidentity.service.MFAManagementService;
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
	
	@Autowired
	private ResourceBundleMessageSource resourceBundle;

	@Autowired
	private MFAManagementService mfaManagementService;

	@Autowired
	private CommonConfiguration commonConfiguration;
	
	@Autowired
	private CprService cprService;

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
		String userId = PersonService.getUsername(person);

		// no reason to confuse the user with a generated UserID if they already have one
		String eBoksUserId = userId;
		if (StringUtils.hasLength(person.getSamaccountName())) {
			eBoksUserId = person.getSamaccountName();
		}

		SendStatus status = eboksService.sendMessage(person.getCpr(),
				"Erhvervsidentitet aktiveret",
				"<p>Din arbejdgiver har aktiveret din erhvervsidentitet, som du kan anvende med nedenstående brugernavn og kodeord.</p><br/>Brugernavn: " + eBoksUserId + "<br/>Kodeord: " + HtmlUtils.htmlEscape(password, "UTF-8"),
				person);

		if (status != SendStatus.SEND) {
			return ResponseEntity.status(500).body("eboks");
		}

		boolean success = activateNSISAccount(person, activationDTO.getNsisLevel(), password, admin);
		if (success) {
			person.setForceChangePassword(true);
			auditLogger.manualActivation(activationDTO.toIdentificationDetails(resourceBundle), person, admin);
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

		boolean success = activateNSISAccount(person, activationDTO.getNsisLevel(), password, admin);
		if (success) {
			person.setForceChangePassword(true);
			activationDTO.setAdminSeenCredentials(true);
			auditLogger.manualActivation(activationDTO.toIdentificationDetails(resourceBundle), person, admin);
			personService.save(person);

			return ResponseEntity.ok(new UsernamePasswordDTO(PersonService.getUsername(person), password));
		}
		
		log.warn("Failed to activate NSIS account for person ID: " + person.getId());

		return ResponseEntity.status(500).body("internal");
	}
	
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

		// deviceId should only be empty for yubikey registration
		if (!StringUtils.hasLength(activationDTO.getDeviceId())) {
			return handleYubikeyRegistration(activationDTO, person, admin);
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
		client.setAssociatedUserTimestamp(new Date());
		
		localRegisteredMfaClientService.save(client);
		
		// add useful logging information
		activationDTO.setNsisLevel(NSISLevel.SUBSTANTIAL);
		activationDTO.setName(mfaClient.getName());
		activationDTO.setType(mfaClient.getType());
		
		auditLogger.manualMfaAssociation(activationDTO.toIdentificationDetails(resourceBundle), person, admin);
		
		return ResponseEntity.ok("");
	}

	private ResponseEntity<?> handleYubikeyRegistration(ActivationDTO activationDTO, Person person, Person admin) {
		String result = mfaManagementService.authenticateUser(person.getCpr(), NSISLevel.SUBSTANTIAL, "yubikey");
		if (result == null) {
			log.warn("Unable to authenitcate user in OS2faktor MFA backend.");
			return ResponseEntity.notFound().build();
		}
		else {
			activationDTO.setNsisLevel(NSISLevel.SUBSTANTIAL);
			auditLogger.manualYubiKeyInitalization(activationDTO.toIdentificationDetails(resourceBundle), person, admin);

			// I'll want some sort of indicator on why I ended up here, right?
			return ResponseEntity.ok(result + "?redirect=" + commonConfiguration.getSelfService().getBaseUrl() + "/admin/registration/mfa");
		}
	}

	private boolean activateNSISAccount(Person person, NSISLevel level, String password, Person admin) {
		if (cprService.checkIsDead(person)) {
			log.error("Could not issue identity to " + person.getId() + " because cpr says the person is dead!");
			return false;
		}
		
		person.setNsisLevel(level);

		try {
			// change password, bypassing validation and AD replication
			// we can ignore the return value because we bypass replication
			personService.changePassword(person, password, true, admin, null, false);
		}
		catch (Exception ex) {
			// this can only fail if there are programming errors, e.g. bad algorithms
			log.warn("Error occured while trying to change password", ex);
			return false;
		}
		
		return true;
	}

	private boolean canBeActivated(Person person) {
		if (person.isNsisAllowed() == false ||
			person.hasActivatedNSISUser() == true) {
			
			return false;
		}

		return true;
	}

	@GetMapping("/admin/registration/changepassword")
	public String passwordChangeUsersList(Model model) {
		return "registration/password/list";
	}

	@GetMapping("/admin/registration/changepassword/{id}")
	public String passwordChangeForm(Model model, @PathVariable Long id) {
		Person person = personService.getById(id);
		if (person == null) {
			return "redirect:/admin/registration/changepassword";
		}
		
		if (!person.isNsisAllowed()) {
			log.warn("Attempting to change password on person that is not NSIS allowd! Person.id = " + person.getId());
			return "redirect:/admin/registration/changepassword";
		}

		ActivationDTO dto = new ActivationDTO();
		dto.setIdentificationType(IdentificationType.PASSPORT);
		dto.setNsisLevel(NSISLevel.SUBSTANTIAL);
		dto.setPersonId(id);
		
		model.addAttribute("person", person);
		model.addAttribute("passwordChangeForm", dto);

		return "registration/password/change";
	}

	@PostMapping("/admin/registration/changepassword/{step}")
	public ResponseEntity<?> passwordChangePost(@Valid @RequestBody ActivationDTO activationDTO, @PathVariable("step") String step, BindingResult bindingResult, HttpServletRequest httpServletRequest) {
		if (step == null || (!"step1".equals(step) && !"step2".equals(step))) {
			log.warn("Bad call to /admin/registration/changepassword/{step}. Step was " + step + " valid are step1 & step2");
			return ResponseEntity.notFound().build();
		}

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

		String password = usernameAndPasswordHelper.generatePassword(person.getDomain());

		if ("step1".equals(step)) {
			SendStatus status = eboksService.sendMessage(person.getCpr(),
					"Nyt kodeord tildelt",
					"<p>Du har fået tildelt et nyt kodeord til brugerkontoen nedenfor</p><br/>Brugernavn: " + PersonService.getUsername(person) + "<br/>Kodeord: " + HtmlUtils.htmlEscape(password, "UTF-8"),
					person);
	
			if (status != SendStatus.SEND) {
				return ResponseEntity.status(500).body("eboks");
			}
		}

		// copy from Person, as we are not changing the NSIS level here
		activationDTO.setNsisLevel(person.getNsisLevel());

		try {
			// note that we are not replicating to AD because this password will be send through e-boks,
			// but once they change it in the UI, it should replicate to AD if needed
			personService.changePassword(person, password, true, admin, null, false);

			person.setForceChangePassword(true);
			if ("step2".equals(step)) {
				activationDTO.setAdminSeenCredentials(true);
			}

			auditLogger.manualPasswordChange(activationDTO.toIdentificationDetails(resourceBundle), person, admin);
			personService.save(person);

			if ("step2".equals(step)) {
				return ResponseEntity.ok(new UsernamePasswordDTO(PersonService.getUsername(person), password));
			}
			else {
				return ResponseEntity.ok().build();
			}
		}
		catch (Exception ex) {
			// this can only fail if there are programming errors, e.g. bad algorithms
			log.warn("Error occured while trying to change password", ex);
		}

		log.warn("Failed to activate NSIS account for person ID: " + person.getId());

		return ResponseEntity.status(500).body("internal");
	}
}
