package dk.digitalidentity.mvc.admin;

import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.KombitJfr;
import dk.digitalidentity.common.dao.model.LocalRegisteredMfaClient;
import dk.digitalidentity.common.dao.model.MitidErhvervCache;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.service.LocalRegisteredMfaClientService;
import dk.digitalidentity.common.service.PasswordSettingService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.mfa.MFAService;
import dk.digitalidentity.common.service.mfa.model.MFAClientDetails;
import dk.digitalidentity.common.service.mfa.model.MfaClient;
import dk.digitalidentity.common.service.model.ADPasswordResponse;
import dk.digitalidentity.common.service.rolecatalogue.RoleCatalogueService;
import dk.digitalidentity.mvc.admin.dto.AdminPersonDTO;
import dk.digitalidentity.mvc.selfservice.NSISStatus;
import dk.digitalidentity.mvc.students.dto.PasswordChangeForm;
import dk.digitalidentity.security.RequirePasswordResetAdmin;
import dk.digitalidentity.security.RequireSupporter;
import dk.digitalidentity.security.SecurityUtil;
import dk.digitalidentity.service.MitidErhvervCacheService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequireSupporter
@Controller
public class IdentitiesController {

	@Autowired
	private MFAService mfaService;

	@Autowired
	private PersonService personService;
	
	@Autowired
	private LocalRegisteredMfaClientService localRegisteredMfaClientService;
	
	@Autowired
	private ResourceBundleMessageSource resourceBundle;

	@Autowired
	private CommonConfiguration commonConfiguration;

	@Autowired
	private RoleCatalogueService roleCatalogueService;

	@Autowired
	private MitidErhvervCacheService midIdErhvervCacheService;

	@Autowired
	private PasswordSettingService passwordSettingService;

	@Autowired
	private SecurityUtil securityUtil;
	
	@GetMapping("/admin/identiteter")
	public String identities(Model model, Locale locale) {
		model.addAttribute("statuses", NSISStatus.getSorted(resourceBundle, locale));
		return "admin/issued-identities";
	}

	@GetMapping("/admin/identiteter/{id}")
	public String view(Model model, @PathVariable Long id) {
		Person person = personService.getById(id);
		if (person == null) {
			return "redirect:/admin/identiteter";
		}

		AdminPersonDTO form = new AdminPersonDTO();
		form.setPersonId(id);
		form.setUserId(PersonService.getUsername(person));
		form.setEmail(person.getEmail());
		form.setAttributes(person.getAttributes());
		form.setKombitAttributes(person.getKombitAttributes());
		
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

		form.setRobot(person.isRobot());
		form.setName((person.isNameProtected() == true && StringUtils.hasLength(person.getNameAlias())) ? person.getNameAlias() : person.getName());
		form.setNameProtected(person.isNameProtected());
		form.setNemloginUserUuid(person.getNemloginUserUuid());
		form.setSchoolRoles(person.getSchoolRoles());

		if (StringUtils.hasLength(person.getNemloginUserUuid())) {
			MitidErhvervCache mitIdCache = midIdErhvervCacheService.findByUuid(person.getNemloginUserUuid());
			if (mitIdCache != null) {
				form.setStatus(mitIdCache.getStatus());
				form.setMitidPrivatCredential(mitIdCache.isMitidPrivatCredential());
				form.setQualifiedSignature(mitIdCache.isQualifiedSignature());
				form.setMitidErhvervRid(mitIdCache.getRid());
			}
		}
		
		model.addAttribute("form", form);

		List<String> roles = new ArrayList<>();
		if (commonConfiguration.getRoleCatalogue().isEnabled() && commonConfiguration.getRoleCatalogue().isKombitRolesEnabled()) {
			roles = roleCatalogueService.getUserRoleNames(person, "KOMBIT");
		}
		else {
			for (KombitJfr kombitJfr : person.getKombitJfrs()) {
				roles.add(kombitJfr.getIdentifier());
			}
		}

		model.addAttribute("roles", roles);

		List<String> groups = person.getGroups().stream().map(gm -> gm.getGroup().getName()).collect(Collectors.toList());
		model.addAttribute("groups", groups);

		return "admin/identity";
	}

	@RequirePasswordResetAdmin
	@GetMapping("/admin/identiteter/resetPassword/{id}")
	public String resetPassword(Model model, @PathVariable Long id) {
		Person person = personService.getById(id);
		if (person == null) {
			return "redirect:/admin/identiteter";
		}

		model.addAttribute("settings", passwordSettingService.getSettings(person));
		model.addAttribute("passwordForm", new PasswordChangeForm(person, false));
		model.addAttribute("disallowNameAndUsernameContent", passwordSettingService.getDisallowedNames(person));

		return "admin/change-password";
	}

	@RequirePasswordResetAdmin
	@PostMapping("/admin/identiteter/resetPassword")
	public String changePassword(Model model, RedirectAttributes redirectAttributes, @Valid @ModelAttribute("passwordForm") PasswordChangeForm form, BindingResult bindingResult) {
		Person personToBeEdited = personService.getById(form.getPersonId());
		if (personToBeEdited == null) {
			redirectAttributes.addFlashAttribute("flashError", "Fejl! Ukendt bruger");
			return "redirect:/admin/identiteter";
		}
		
		if (personToBeEdited.isNsisAllowed()) {
			redirectAttributes.addFlashAttribute("flashError", "Fejl! Brugeren har en erhvervsidentitet - kodeordsskifte ikke tilladt");
			return "redirect:/admin/identiteter";			
		}

		// Check for password errors
		if (bindingResult.hasErrors()) {
			model.addAttribute(bindingResult.getAllErrors());
			model.addAttribute("passwordForm", form);
			model.addAttribute("settings", passwordSettingService.getSettings(personToBeEdited));
			model.addAttribute("disallowNameAndUsernameContent", passwordSettingService.getDisallowedNames(personToBeEdited));

			return "admin/change-password";
		}

		try {
			Person loggedInPerson = securityUtil.getPerson();
			if (loggedInPerson == null) {
				log.warn("Person ikke logget ind, session timeout?");
				redirectAttributes.addFlashAttribute("flashError", "Fejl! Ukendt administrator");

				return "redirect:/admin/identiteter";
			}

			ADPasswordResponse.ADPasswordStatus adPasswordStatus = personService.changePasswordByAdmin(personToBeEdited, form.getPassword(), loggedInPerson, true);

			if (ADPasswordResponse.isCritical(adPasswordStatus)) {
				if (adPasswordStatus.equals(ADPasswordResponse.ADPasswordStatus.TECHNICAL_ERROR)) {
					model.addAttribute("technicalError", true);
				}
				else if (adPasswordStatus.compareTo(ADPasswordResponse.ADPasswordStatus.FAILURE) == 0) {
					model.addAttribute("connectionFailure", true);
				}
				else if (adPasswordStatus.equals(ADPasswordResponse.ADPasswordStatus.INSUFFICIENT_PERMISSION)) {
					model.addAttribute("insufficientPermission", true);
				}

				model.addAttribute("settings", passwordSettingService.getSettings(personToBeEdited));
				model.addAttribute("disallowNameAndUsernameContent", passwordSettingService.getDisallowedNames(personToBeEdited));

				return "admin/change-password";
			}
		}
		catch (NoSuchPaddingException | InvalidKeyException | NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException | UnsupportedEncodingException | InvalidAlgorithmParameterException e) {
			log.error("Exception while trying to change password on another user", e);

			redirectAttributes.addFlashAttribute("flashError", "Fejl! Der opstod en teknisk fejl");
			return "redirect:/admin/identiteter";
		}

		redirectAttributes.addFlashAttribute("flashSuccess", "Kodeord ændret");

		return "redirect:/admin/identiteter";
	}

	@GetMapping("/admin/fragment/user-mfa-devices/{id}")
	public String mfaDevices(Model model, @PathVariable Long id) {
		List<MfaClient> clients = new ArrayList<MfaClient>();

		Person person = personService.getById(id);
		if (person != null) {
			clients = mfaService.getClients(person.getCpr(), person.isRobot());
		}

		model.addAttribute("clients", clients);
		model.addAttribute("showDeleteAction", false);
		model.addAttribute("showDetailsAction", true);
		model.addAttribute("showLocalDeleteAction", true);
		model.addAttribute("showPrimaryAction", false);
		model.addAttribute("showPasswordless", commonConfiguration.getCustomer().isEnablePasswordlessMfa());

		// re-use fragment from selfservice...
		return "selfservice/fragments/mfa-devices :: table";
	}

	@GetMapping("/admin/fragment/modal/mfa/{deviceId}/details")
	public ModelAndView getMFADeviceRegistrationDetails(Model model, @PathVariable("deviceId") String deviceId) {
		// Check if device is a locally registered device
		LocalRegisteredMfaClient localRegisteredMfaClient = localRegisteredMfaClientService.getByDeviceId(deviceId);
		model.addAttribute("localClient", localRegisteredMfaClient != null);

		// Get details from os2faktor MFA backend
		MFAClientDetails body = mfaService.getClientDetails(deviceId);
		if (body == null) {
			ModelAndView modelAndView = new ModelAndView("error");
			modelAndView.setStatus(HttpStatus.BAD_REQUEST);

			return modelAndView;
		}

		// if we have a local client with a AssociatedUserTimestamp,
		// show it in details page if MFA Service did not return any AssociatedUserTimestamp value
		if (localRegisteredMfaClient != null && localRegisteredMfaClient.getAssociatedUserTimestamp() != null && body.getAssociatedUserTimestamp() == null) {
			body.setAssociatedUserTimestamp(localRegisteredMfaClient.getAssociatedUserTimestamp());
		}

		model.addAttribute("client", body);

		return new ModelAndView("selfservice/fragments/mfa-devices :: detailsModal");
	}
}
