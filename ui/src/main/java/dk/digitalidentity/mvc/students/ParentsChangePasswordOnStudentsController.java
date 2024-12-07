package dk.digitalidentity.mvc.students;

import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.service.PasswordSettingService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.model.ADPasswordResponse;
import dk.digitalidentity.mvc.otherUsers.validator.PasswordChangeValidator;
import dk.digitalidentity.mvc.students.dto.PasswordChangeForm;
import dk.digitalidentity.security.RequireParent;
import dk.digitalidentity.security.SecurityUtil;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class ParentsChangePasswordOnStudentsController {

	@Autowired
	private SecurityUtil securityUtil;

	@Autowired
	private PersonService personService;

	@Autowired
	private PasswordSettingService passwordSettingService;

	@Autowired
	private PasswordChangeValidator passwordChangeFormValidator;

	@Autowired
	private CommonConfiguration commonConfiguration;

	@InitBinder("passwordForm")
	public void initClientBinder(WebDataBinder binder) {
		binder.setValidator(passwordChangeFormValidator);
	}

	@GetMapping("/elevkode")
	public String index() {
		if (!commonConfiguration.getStilStudent().isEnabled()) {
			return "redirect:/";
		}
		
		return "students/password-change-parent/welcome";
	}

	@RequireParent
	@GetMapping("/elevkode/skiftkodeord")
	public String studentList(Model model, RedirectAttributes redirectAttributes) {
		if (!commonConfiguration.getStilStudent().isEnabled()) {
			return "redirect:/";
		}

		String cpr = securityUtil.getCpr();
		if (!StringUtils.hasLength(cpr)) {
			log.warn("Person ikke logget ind, session timeout?");
			redirectAttributes.addFlashAttribute("flashError", "Fejl! Du er ikke logget ind");

			return "redirect:/elevkode";
		}

		List<Person> children = personService.getChildrenPasswordAllowed(cpr);
		model.addAttribute("children", children);

		return "students/password-change-parent/list";
	}

	@RequireParent
	@GetMapping("/elevkode/skiftkodeord/{id}")
	public String changePassword(Model model, RedirectAttributes redirectAttributes, @PathVariable("id") long id) {
		if (!commonConfiguration.getStilStudent().isEnabled()) {
			return "redirect:/";
		}

		Person personToBeEdited = personService.getById(id);
		if (!allowedPasswordChange(personToBeEdited)) {
			redirectAttributes.addFlashAttribute("flashError", "Fejl! Det er ikke tilladt at skifte kodeord på denne bruger");
			return "redirect:/elevkode/skiftkodeord";
		}

		model.addAttribute("settings", passwordSettingService.getSettings(personToBeEdited));
		model.addAttribute("passwordForm", new PasswordChangeForm(personToBeEdited, false));

		return "students/password-change-parent/change-password";
	}

	@RequireParent
	@PostMapping("/elevkode/skiftkodeord/skift")
	public String changePassword(Model model, RedirectAttributes redirectAttributes, @Valid @ModelAttribute("passwordForm") PasswordChangeForm form, BindingResult bindingResult) {
		if (!commonConfiguration.getStilStudent().isEnabled()) {
			return "redirect:/";
		}

		Person personToBeEdited = personService.getById(form.getPersonId());
		if (personToBeEdited == null) {
			redirectAttributes.addFlashAttribute("flashError", "Fejl! Ukendt bruger");
			return "redirect:/elevkode/skiftkodeord";
		}

		if (!allowedPasswordChange(personToBeEdited)) {
			redirectAttributes.addFlashAttribute("flashError", "Fejl! Det er ikke tilladt at skifte kodeord på denne bruger");
			return "redirect:/elevkode/skiftkodeord";
		}

		// Check for password errors
		if (bindingResult.hasErrors()) {
			model.addAttribute("settings", passwordSettingService.getSettings(personToBeEdited));

			return "students/password-change-parent/change-password";
		}

		try {
			String cpr = securityUtil.getCpr();

			if (!StringUtils.hasLength(cpr)) {
				log.warn("Person ikke logget ind, session timeout?");
				redirectAttributes.addFlashAttribute("flashError", "Fejl! Du er ikke logget ind");

				return "redirect:/elevkode";
			}

			ADPasswordResponse.ADPasswordStatus adPasswordStatus = personService.changePasswordByParent(personToBeEdited, form.getPassword(), cpr);

			// force change password on next login through IdP
			personToBeEdited.setForceChangePassword(true);
			personService.save(personToBeEdited);

			if (ADPasswordResponse.isCritical(adPasswordStatus)) {
				model.addAttribute("technicalError", true);
				model.addAttribute("settings", passwordSettingService.getSettings(personToBeEdited));

				return "students/password-change-parent/change-password";
			}
		}
		catch (NoSuchPaddingException | InvalidKeyException | NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException | UnsupportedEncodingException | InvalidAlgorithmParameterException e) {
			log.error("Exception while trying to change password on another user", e);

			redirectAttributes.addFlashAttribute("flashError", "Fejl! Der opstod en teknisk fejl");
			return "redirect:/elevkode/skiftkodeord";
		}

		redirectAttributes.addFlashAttribute("flashSuccess", "Kodeord ændret");

		return "redirect:/elevkode/skiftkodeord";
	}

	private boolean allowedPasswordChange(Person personToBeEdited) {
		String loggedInPerson = securityUtil.getCpr();
		if (loggedInPerson == null) {
			return false;
		}

		if (personToBeEdited == null) {
			return false;
		}

		if (personToBeEdited.isNsisAllowed()) {
			return false;
		}
		
		if (personService.getChildrenPasswordAllowed(loggedInPerson).stream().anyMatch(c -> c.getUuid().equals(personToBeEdited.getUuid()))) {
			return true;
		}

		return false;
	}
}
