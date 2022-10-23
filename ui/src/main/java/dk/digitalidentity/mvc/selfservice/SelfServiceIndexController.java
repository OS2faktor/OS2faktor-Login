package dk.digitalidentity.mvc.selfservice;

import java.util.Comparator;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.mfa.MFAService;
import dk.digitalidentity.common.service.mfa.model.MfaClient;
import dk.digitalidentity.mvc.selfservice.dto.SelfServicePersonDTO;
import dk.digitalidentity.security.SecurityUtil;
import dk.digitalidentity.service.MFAManagementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Slf4j
@Controller
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

	@GetMapping("/selvbetjening")
	public String index(Model model, HttpServletRequest request, @RequestParam(required = false) boolean skipChangePassword) {
		Person person = personService.getById(securityUtil.getPersonId());
		if (person == null) {
			log.warn("Tried to access system without being logged in");
			return "redirect:/error";
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

	@GetMapping("/selvbetjening/fragment/mfa-devices")
	public String mfaDevices(Model model) {
		Person person = personService.getById(securityUtil.getPersonId());
		if (person != null) {
			List<MfaClient> clients = mfaService.getClients(person.getCpr());
			clients.sort(Comparator.comparing(MfaClient::getDeviceId));
			model.addAttribute("clients", clients);
		}

		model.addAttribute("showDeleteAction", true);
		model.addAttribute("showPrimaryAction", false);
		model.addAttribute("showDetailsAction", false);
		model.addAttribute("showLocalDeleteAction", false);

		return "selfservice/fragments/mfa-devices :: table";
	}

	@GetMapping("/selvbetjening/fragment/mfa-devices-primary")
	public String mfaDevicesPrimary(Model model) {
		Person person = personService.getById(securityUtil.getPersonId());
		if (person != null) {
			List<MfaClient> clients = mfaService.getClients(person.getCpr());
			clients.sort(Comparator.comparing(MfaClient::getDeviceId));
			model.addAttribute("clients", clients);
		}

		model.addAttribute("showDeleteAction", false);
		model.addAttribute("showPrimaryAction", true);
		model.addAttribute("showDetailsAction", false);
		model.addAttribute("showLocalDeleteAction", false);
		
		return "selfservice/fragments/mfa-devices :: table";
	}

	@GetMapping("/selvbetjening/tilfoej")
	public String registerYubikeyOrAuthenticator(Model model, @RequestParam(defaultValue = "yubikey") String type, RedirectAttributes redirectAttributes) {
		Person person = personService.getById(securityUtil.getPersonId());
		if (person == null) {
			log.warn("Tried to access system without being logged in");
			return "redirect:/error";
		}
		
		if (!"yubikey".equals(type) && !"authenticator".equals(type)) {
			log.warn("Tried to add yubikey or authenticator but type was " + type);
			return "redirect:/error";
		}

		NSISLevel nsisLevel = person.getNsisLevel() == NSISLevel.SUBSTANTIAL ? NSISLevel.SUBSTANTIAL : NSISLevel.LOW;

		String result = mfaManagementService.authenticateUser(person.getCpr(), nsisLevel, type);
		if (result == null) {
			redirectAttributes.addFlashAttribute("flashWarnMessage", "Der opstod en teknisk fejl");

			return "redirect:/selvbetjening";
		}
		else {
			return "redirect:" + result + "?redirect=" + commonConfiguration.getSelfService().getBaseUrl() + "/selvbetjening";
		}
	}
	
	@GetMapping("/selvbetjening/unlockADAccount")
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
