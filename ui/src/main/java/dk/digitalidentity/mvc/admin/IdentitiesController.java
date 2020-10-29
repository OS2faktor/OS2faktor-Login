package dk.digitalidentity.mvc.admin;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.mfa.MFAService;
import dk.digitalidentity.common.service.mfa.model.MfaClient;
import dk.digitalidentity.config.Constants;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.mvc.admin.dto.AdminPersonDTO;
import dk.digitalidentity.mvc.selfservice.dto.SelfServiceStatus;
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
	private OS2faktorConfiguration configuration;

	@GetMapping("/admin/identiteter")
	public String identities(Model model) {
		model.addAttribute("coreDataEditable", securityUtil.hasRole(Constants.ROLE_COREDATA_EDITOR));
		model.addAttribute("configDomain", configuration.getCoreData().getDomain());
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
		form.setUserId(person.hasNSISUser() ? person.getUserId() : "<Ingen erhvervsidentitet udstedt>");
		form.setStatus(person.hasNSISUser() ? (person.isLocked() ? SelfServiceStatus.BLOCKED : SelfServiceStatus.ACTIVE) : SelfServiceStatus.NOT_ISSUED);
		form.setEmail(person.getEmail());
		form.setAttributes(person.getAttributes());

		if (person.hasNSISUser()) {
			if (person.isLockedDataset()) {
				form.setStatusMessage("page.admin.issuedidentitites.status.lockedDataset");
			}
			else if (person.isLockedAdmin()) {
				form.setStatusMessage("page.admin.issuedidentitites.status.lockedAdmin");
			}
			else if (person.isLockedPerson()) {
				form.setStatusMessage("page.admin.issuedidentitites.status.lockedPerson");
			}
			else if (person.isLockedPassword()) {
				form.setStatusMessage("page.admin.issuedidentitites.status.lockedPassword");
			}
		}

		form.setNsisLevel(person.getNsisLevel());
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

		// re-use fragment from selfservice...
		return "selfservice/fragments/mfa-devices :: table";
	}
}
