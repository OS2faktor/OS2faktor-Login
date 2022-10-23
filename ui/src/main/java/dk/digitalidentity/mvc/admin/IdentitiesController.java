package dk.digitalidentity.mvc.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.ModelAndView;

import dk.digitalidentity.common.dao.model.LocalRegisteredMfaClient;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.service.DomainService;
import dk.digitalidentity.common.service.LocalRegisteredMfaClientService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.mfa.MFAService;
import dk.digitalidentity.common.service.mfa.model.MFAClientDetails;
import dk.digitalidentity.common.service.mfa.model.MfaClient;
import dk.digitalidentity.config.Constants;
import dk.digitalidentity.mvc.admin.dto.AdminPersonDTO;
import dk.digitalidentity.mvc.selfservice.NSISStatus;
import dk.digitalidentity.security.RequireSupporter;
import dk.digitalidentity.security.SecurityUtil;

@RequireSupporter
@Controller
public class IdentitiesController {

	@Autowired
	private MFAService mfaService;

	@Autowired
	private PersonService personService;

	@Autowired
	private SecurityUtil securityUtil;
	
	@Autowired
	private LocalRegisteredMfaClientService localRegisteredMfaClientService;

	@Autowired
	private DomainService domainService;
	
	@Autowired
	private ResourceBundleMessageSource resourceBundle;

	@GetMapping("/admin/identiteter")
	public String identities(Model model, Locale locale) {
		model.addAttribute("statuses", NSISStatus.getSorted(resourceBundle, locale));
		model.addAttribute("coreDataEditable", securityUtil.hasRole(Constants.ROLE_COREDATA_EDITOR));
		model.addAttribute("configDomain", domainService.getInternalDomain().getName());
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
		form.setNameProtected(person.isNameProtected());
		form.setSchoolRoles(person.getSchoolRoles());
		
		model.addAttribute("form", form);

		return "admin/identity";
	}

	@GetMapping("/admin/fragment/user-mfa-devices/{id}")
	public String mfaDevices(Model model, @PathVariable Long id) {
		List<MfaClient> clients = new ArrayList<MfaClient>();

		Person person = personService.getById(id);
		if (person != null) {
			clients = mfaService.getClients(person.getCpr());
		}

		model.addAttribute("clients", clients);
		model.addAttribute("showDeleteAction", false);
		model.addAttribute("showDetailsAction", true);
		model.addAttribute("showLocalDeleteAction", true);
		model.addAttribute("showPrimaryAction", false);

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
