package dk.digitalidentity.mvc.selfservice;

import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.mfa.MFAManagementService;
import dk.digitalidentity.common.service.mfa.MFAService;
import dk.digitalidentity.common.service.mfa.model.ClientType;
import dk.digitalidentity.common.service.mfa.model.MfaClient;
import dk.digitalidentity.mvc.admin.dto.ActivationDTO;
import dk.digitalidentity.mvc.selfservice.dto.SelfServicePersonDTO;
import dk.digitalidentity.security.RequireEmployee;
import dk.digitalidentity.security.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequireEmployee
public class SelfServiceIndexController {

	@Autowired
	private MFAService mfaService;

	@Autowired
	private SecurityUtil securityUtil;

	@Autowired
	private PersonService personService;

	@Autowired
	private MFAManagementService mfaManagementService;

	@Autowired
	private CommonConfiguration commonConfiguration;

	@Autowired
	private AuditLogger auditLogger;

	@Autowired
	private ResourceBundleMessageSource resourceBundle;

	@GetMapping("/selvbetjening")
	public String index(Model model, HttpServletRequest request, @RequestParam(required = false) boolean skipChangePassword, @RequestParam(required = false) boolean result, @RequestParam(required = false) String deviceId, @RequestParam(required = false) String name) {
		Person person = personService.getById(securityUtil.getPersonId());
		if (person == null) {
			log.warn("Tried to access system without being logged in");
			return "redirect:/error";
		}

		// check if person has returned from registering a client and if the result was true. Then auditlog
		if (result) {
			checkIfAuditlog(request, deviceId, name, person);
		}

		if (skipChangePassword) {
			request.getSession().setAttribute("skipChangePassword", true);
		}
		
		boolean bypass = false;
		if (request.getSession().getAttribute("skipChangePassword") != null) {
			bypass = (boolean) request.getSession().getAttribute("skipChangePassword");
		}

		if (securityUtil.hasNSISUserAndLoggedInWithNSISNone() && !bypass) {
			model.addAttribute("currentBaseUrl", ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString());
			return "selfservice/login-bad-password-state";
		}

		SelfServicePersonDTO form = new SelfServicePersonDTO();
		form.setUserId(PersonService.getUsername(person));
		form.setEmail(person.getEmail());

		if (person.isLocked()) {
			if (person.isLockedAdmin() || person.isLockedDataset()) {
				form.setNsisStatus(NSISStatus.LOCKED_BY_MUNICIPALITY);
			}
			else if (person.isLockedPerson() || person.isLockedPassword()) {
				form.setNsisStatus(NSISStatus.LOCKED_BY_SELF);
			}
			else if (person.isLockedExpired()) {
				form.setNsisStatus(NSISStatus.LOCKED_BY_EXPIRE);
			}
			else {
				form.setNsisStatus(NSISStatus.LOCKED_BY_STATUS);
			}
		}
		else if (person.isNsisAllowed()) {
			if (!person.hasActivatedNSISUser()) {
				form.setNsisStatus(NSISStatus.NOT_ACTIVATED);
			}
			else {
				form.setNsisStatus(NSISStatus.ACTIVE);
			}
		}
		else {
			form.setNsisStatus(NSISStatus.NOT_ISSUED);
		}

		form.setName((person.isNameProtected() == true && StringUtils.hasLength(person.getNameAlias())) ? person.getNameAlias() : person.getName());
		form.setSchoolRoles(person.getSchoolRoles());
		model.addAttribute("form", form);
		
		return "selfservice/index";
	}

	private void checkIfAuditlog(HttpServletRequest request, String deviceId, String name, Person person) {
		boolean save = false;
		ActivationDTO activationDTO = new ActivationDTO();
		activationDTO.setDeviceId(deviceId);
		activationDTO.setName(name);
		activationDTO.setNsisLevel(person.getNsisLevel());

		if (request.getSession().getAttribute("inYubikeyFlow") != null) {
			boolean inYubikeyFlow = (boolean) request.getSession().getAttribute("inYubikeyFlow");
			if (inYubikeyFlow) {
				save = true;
				activationDTO.setType(ClientType.YUBIKEY);
				request.getSession().setAttribute("inYubikeyFlow", null);
			}
		}
		else if (request.getSession().getAttribute("inAuthenticatorFlow") != null) {
			boolean inAuthenticatorFlow = (boolean) request.getSession().getAttribute("inAuthenticatorFlow");
			if (inAuthenticatorFlow) {
				save = true;
				activationDTO.setType(ClientType.TOTP);
				request.getSession().setAttribute("inAuthenticatorFlow", null);
			}
		}
		else if (request.getSession().getAttribute("inTOTPHFlow") != null) {
			boolean inTOTPHFlow = (boolean) request.getSession().getAttribute("inTOTPHFlow");
			if (inTOTPHFlow) {
				save = true;
				activationDTO.setType(ClientType.TOTPH);
				request.getSession().setAttribute("inTOTPHFlow", null);
			}
		}

		if (save) {
			auditLogger.manualMfaAssociation(activationDTO.toIdentificationDetails(resourceBundle), person);
		}
	}

	@GetMapping("/selvbetjening/fragment/mfa-devices")
	public String mfaDevices(Model model) {
		Person person = personService.getById(securityUtil.getPersonId());
		if (person != null) {
			List<MfaClient> clients = mfaService.getClients(person.getCpr(), person.isRobot());
			clients.sort(Comparator.comparing(MfaClient::getDeviceId));
			model.addAttribute("clients", clients);
		}

		model.addAttribute("showDeleteAction", true);
		model.addAttribute("showEditAction", true);
		model.addAttribute("showPrimaryAction", false);
		model.addAttribute("showDetailsAction", false);
		model.addAttribute("showLocalDeleteAction", false);
		model.addAttribute("showPasswordless", commonConfiguration.getCustomer().isEnablePasswordlessMfa());

		return "selfservice/fragments/mfa-devices :: table";
	}

	@GetMapping("/selvbetjening/fragment/mfa-devices-primary")
	public String mfaDevicesPrimary(Model model) {
		Person person = personService.getById(securityUtil.getPersonId());
		if (person != null) {
			List<MfaClient> clients = mfaService.getClients(person.getCpr(), person.isRobot());
			clients.sort(Comparator.comparing(MfaClient::getDeviceId));
			model.addAttribute("clients", clients);
		}

		model.addAttribute("showDeleteAction", false);
		model.addAttribute("showEditAction", false);
		model.addAttribute("showPrimaryAction", true);
		model.addAttribute("showDetailsAction", false);
		model.addAttribute("showLocalDeleteAction", false);
		model.addAttribute("showPasswordless", false);
		
		return "selfservice/fragments/mfa-devices :: table";
	}

	@GetMapping("/selvbetjening/tilfoej")
	public String registerYubikeyOrAuthenticator(Model model, HttpServletRequest request, @RequestParam(defaultValue = "yubikey") String type, RedirectAttributes redirectAttributes) {
		Person person = personService.getById(securityUtil.getPersonId());
		if (person == null) {
			log.warn("Tried to access system without being logged in");
			return "redirect:/error";
		}
		
		if ("yubikey".equals(type)) {
			request.getSession().setAttribute("inYubikeyFlow", true);
		}
		else if ("authenticator".equals(type)){
			request.getSession().setAttribute("inAuthenticatorFlow", true);
		}
		else if ("kodeviser".equals(type)) {
			request.getSession().setAttribute("inTOTPHFlow", true);
		}
		else {
			log.warn("Tried to add yubikey or authenticator but type was " + type);
			return "redirect:/error";
		}

		NSISLevel nsisLevel = securityUtil.getAuthenticationAssuranceLevel();

		String result = mfaManagementService.authenticateUser(person.getCpr(), nsisLevel, type);
		if (result == null) {
			redirectAttributes.addFlashAttribute("flashWarnMessage", "Der opstod en teknisk fejl");

			return "redirect:/selvbetjening";
		}
		else {
			return "redirect:" + result + "?redirect=" + commonConfiguration.getSelfService().getBaseUrl() + "/selvbetjening";
		}
	}
	
	@GetMapping("/selvbetjening/unlockAccount")
	public String unlockAccount(Model model) {
		Person person = personService.getById(securityUtil.getPersonId());
		if (person == null) {
			log.warn("Tried to access system without being logged in");
			return "redirect:/error";
		}
		
		model.addAttribute("person", person);

		return "selfservice/unlock-ad-account";
	}
}
