package dk.digitalidentity.mvc.students;

import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.PasswordSetting;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.service.GroupService;
import dk.digitalidentity.common.service.PasswordSettingService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.model.ADPasswordResponse;
import dk.digitalidentity.mvc.otherUsers.validator.PasswordChangeValidator;
import dk.digitalidentity.mvc.students.dto.PasswordChangeForm;
import dk.digitalidentity.security.RequireChangePasswordOnOthersRole;
import dk.digitalidentity.security.SecurityUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequireChangePasswordOnOthersRole
@Controller
public class ChangePasswordOnStudentsController {

	@Autowired
	private PersonService personService;

	@Autowired
	private PasswordSettingService passwordSettingService;

	@Autowired
	private SecurityUtil securityUtil;

	@Autowired
	private PasswordChangeValidator passwordChangeFormValidator;

	@Autowired
	private CommonConfiguration commonConfiguration;

	@InitBinder("passwordForm")
	public void initClientBinder(WebDataBinder binder) {
		binder.setValidator(passwordChangeFormValidator);
	}

	@GetMapping("/andre-brugere/kodeord/skift/list")
	public String getList(Model model) {
		Person loggedInPerson = securityUtil.getPerson();
		if (loggedInPerson == null) {
			return "redirect:/";
		}

		model.addAttribute("people", personService.getStudentsThatPasswordCanBeChangedOnByPerson(loggedInPerson));

		return "students/password-change/list.html";
	}

	@GetMapping("/andre-brugere/{id}/kodeord/skift")
	public String changePassword(Model model, RedirectAttributes redirectAttributes, @PathVariable("id") long id) {
		Person personToBeEdited = personService.getById(id);
		if (!allowedPasswordChange(personToBeEdited)) {
			redirectAttributes.addFlashAttribute("flashError", "Fejl! Det er ikke tilladt at skifte kodeord på denne bruger");
			return "redirect:/andre-brugere/kodeord/skift/list";
		}

		model.addAttribute("settings", passwordSettingService.getSettings(personToBeEdited.getDomain()));
		model.addAttribute("passwordForm", new PasswordChangeForm(personToBeEdited));

		return "students/password-change/change-password";
	}

	@PostMapping("/andre-brugere/kodeord/skift")
	public String changePassword(Model model, RedirectAttributes redirectAttributes, @Valid @ModelAttribute("passwordForm") PasswordChangeForm form, BindingResult bindingResult) {
		Person personToBeEdited = personService.getById(form.getPersonId());
		if (personToBeEdited == null) {
			redirectAttributes.addFlashAttribute("flashError", "Fejl! Ukendt bruger");
			return "redirect:/andre-brugere/kodeord/skift/list";
		}

		if (!allowedPasswordChange(personToBeEdited)) {
			redirectAttributes.addFlashAttribute("flashError", "Fejl! Det er ikke tilladt at skifte kodeord på denne bruger");
			return "redirect:/andre-brugere/kodeord/skift/list";
		}

		// Check for password errors
		if (bindingResult.hasErrors()) {
			model.addAttribute("settings", passwordSettingService.getSettings(personToBeEdited.getDomain()));

			return "students/password-change/change-password";
		}

		try {
			Person loggedInPerson = securityUtil.getPerson();

			if (loggedInPerson == null) {
				log.warn("Person ikke logget ind, session timeout?");
				redirectAttributes.addFlashAttribute("flashError", "Fejl! Ukendt administrator");

				return "redirect:/andre-brugere/kodeord/skift/list";
			}

			ADPasswordResponse.ADPasswordStatus adPasswordStatus = personService.changePassword(personToBeEdited, form.getPassword(), false, loggedInPerson);

			if (ADPasswordResponse.isCritical(adPasswordStatus)) {
				model.addAttribute("technicalError", true);
				model.addAttribute("settings", passwordSettingService.getSettings(personToBeEdited.getDomain()));

				return "students/password-change/change-password";
			}
		}
		catch (NoSuchPaddingException | InvalidKeyException | NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException | UnsupportedEncodingException | InvalidAlgorithmParameterException e) {
			log.error("Exception while trying to change password on another user", e);

			redirectAttributes.addFlashAttribute("flashError", "Fejl! Der opstid en teknisk fejl");
			return "redirect:/andre-brugere/kodeord/skift/list";
		}

		redirectAttributes.addFlashAttribute("flashSuccess", "Kodeord ændret");

		return "redirect:/andre-brugere/kodeord/skift/list";
	}

	private boolean allowedPasswordChange(Person personToBeEdited) {
		if (!commonConfiguration.getStilStudent().isEnabled()) {
			return false;
		}

		Person loggedInPerson = personService.getById(securityUtil.getPersonId());
		if (loggedInPerson == null) {
			return false;
		}

		if (personToBeEdited == null) {
			return false;
		}

		if (personToBeEdited.isNsisAllowed()) {
			return false;
		}

		PasswordSetting settings = passwordSettingService.getSettings(personToBeEdited.getDomain());
		if (settings.isCanNotChangePasswordEnabled() && settings.getCanNotChangePasswordGroup() != null && GroupService.memberOfGroup(personToBeEdited, Collections.singletonList(settings.getCanNotChangePasswordGroup()))) {
			return false;
		}

		if (personService.getStudentsThatPasswordCanBeChangedOnByPerson(loggedInPerson).stream().anyMatch(p -> p.getUuid().equals(personToBeEdited.getUuid()))) {
			return true;
		}

		return false;
	}
}
