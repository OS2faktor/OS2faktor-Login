package dk.digitalidentity.mvc.selfservice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.security.SecurityUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class StateController {

	@Autowired
	private SecurityUtil securityUtils;

	@Autowired
	private PersonService personService;

	@Autowired
	private AuditLogger auditLogger;

	@GetMapping("/selvbetjening/spaerre")
	public String getLockAccount() {
		Person person = personService.getById(securityUtils.getPersonId());
		if (person == null || person.isLockedPerson() || !person.hasActivatedNSISUser()) {
			return "redirect:/selvbetjening";
		}

		return "selfservice/lock-account";
	}

	@PostMapping("/selvbetjening/spaerre")
	public String postLockAccount(RedirectAttributes redirectAttributes) {
		Person person = personService.getById(securityUtils.getPersonId());
		if (person == null) {
			log.error("Person did not exist: " + securityUtils.getPersonId());
			return "redirect:/selvbetjening";
		}

		if (!person.hasActivatedNSISUser()) {
			log.warn("Person tried to lock account but person does not have an NSIS account. (" + person.getUuid() + ")");
			return "redirect:/selvbetjening";
		}

		if (person.isLockedPerson()) {
			log.warn("Person tried to lock account, but the account was aleady locked by person. (" + person.getUuid() + ")");
			return "redirect:/selvbetjening";
		}

		person.setLockedPerson(true);
		personService.suspend(person);
		person = personService.save(person);

		auditLogger.deactivateByPerson(person);
		securityUtils.updateTokenUser(person);
		
		redirectAttributes.addFlashAttribute("flashMessage", "Din identitet er spærret");

		return "redirect:/selvbetjening";
	}

	@GetMapping("/selvbetjening/genaktiver")
	public String getReactivateAccount() {
		Person person = personService.getById(securityUtils.getPersonId());
		if (person == null || !person.isLockedPerson() || !person.hasActivatedNSISUser()) {
			return "redirect:/selvbetjening";
		}

		return "selfservice/reactivate-account";
	}

	@PostMapping("/selvbetjening/genaktiver")
	public String postReactivateAccount() {
		Person person = personService.getById(securityUtils.getPersonId());
		if (person == null) {
			log.error("Person did not exists: " + securityUtils.getPersonId());
			return "redirect:/selvbetjening";
		}

		if (!person.hasActivatedNSISUser()) {
			log.warn("Person tried to unlock account but person does not have an NSIS account. (" + person.getUuid() + ")");
			return "redirect:/selvbetjening";
		}

		if (!person.isLockedPerson()) {
			log.warn("Person tried to unlock account, but the account is already unlocked. (" + person.getUuid() + ")");
			return "redirect:/selvbetjening";
		}

		person.setLockedPerson(false);
		person = personService.save(person);

		auditLogger.reactivateByPerson(person);
		securityUtils.updateTokenUser(person);

		return "redirect:/selvbetjening";
	}
}
