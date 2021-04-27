package dk.digitalidentity.mvc.selfservice;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.mfa.MFAService;
import dk.digitalidentity.common.service.mfa.model.MfaClient;
import dk.digitalidentity.mvc.selfservice.dto.SelfServicePersonDTO;
import dk.digitalidentity.mvc.selfservice.dto.SelfServiceStatus;
import dk.digitalidentity.security.SecurityUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class SelfServiceIndexController {

	@Autowired
	private MFAService mfaService;

	@Autowired
	private SecurityUtil securityUtil;

	@Autowired
	private PersonService personService;

	@GetMapping("/selvbetjening")
	public String index(Model model) {
		Person person = personService.getById(securityUtil.getPersonId());
		if (person == null) {
			log.warn("Tried to access system without being logged in");
			return "redirect:/error";
		}

		SelfServicePersonDTO form = new SelfServicePersonDTO();
		form.setUserId(person.hasNSISUser() ? person.getUserId() : "<Ingen erhvervsidentitet udstedt>");
		form.setSamAccountName(person.getSamaccountName() != null ? person.getSamaccountName() : "");
		form.setStatus(person.hasNSISUser() ? (person.isLocked() ? SelfServiceStatus.BLOCKED : SelfServiceStatus.ACTIVE) : SelfServiceStatus.NOT_ISSUED);
		form.setEmail(person.getEmail());

		if (person.hasNSISUser()) {
			if (person.isLockedDataset()) {
				form.setStatusMessage("page.selfservice.index.status.lockedDataset");
			}
			else if (person.isLockedAdmin()) {
				form.setStatusMessage("page.selfservice.index.status.lockedAdmin");
			}
			else if (person.isLockedPerson()) {
				form.setStatusMessage("page.selfservice.index.status.lockedPerson");
			}
			else if (person.isLockedPassword()) {
				form.setStatusMessage("page.selfservice.index.status.lockedPassword");
			}
		}

		form.setNsisLevel(person.getNsisLevel());
		form.setName(person.getName());
		form.setAlias(person.getNameAlias());
		model.addAttribute("form", form);

		return "selfservice/index";
	}

	@GetMapping("/selvbetjening/fragment/mfa-devices")
	public String mfaDevices(Model model) {
		Person person = personService.getById(securityUtil.getPersonId());
		if (person != null) {
			List<MfaClient> clients = mfaService.getClients(person.getCpr());
			model.addAttribute("clients", clients);
		}

		return "selfservice/fragments/mfa-devices :: table";
	}
}
