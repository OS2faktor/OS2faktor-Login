package dk.digitalidentity.mvc.selfservice;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import dk.digitalidentity.common.dao.model.PasswordSetting;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.service.PasswordSettingService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.mvc.selfservice.dto.PasswordChangeForm;
import dk.digitalidentity.mvc.selfservice.validator.PasswordChangeValidator;
import dk.digitalidentity.security.SecurityUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class ChangePasswordController {

	@Autowired
	private PersonService personService;

	@Autowired
	private SecurityUtil securityUtil;

	@Autowired
	private PasswordSettingService passwordService;

	@Autowired
	private PasswordChangeValidator passwordChangeFormValidator;
	
	@InitBinder("passwordForm")
	public void initClientBinder(WebDataBinder binder) {
		binder.setValidator(passwordChangeFormValidator);
	}

	@GetMapping("/selvbetjening/skiftkode")
	public String getChangePassword(Model model) {
		PasswordSetting settings = passwordService.getSettings();
		PasswordChangeForm form = new PasswordChangeForm();

		Person person = personService.getById(securityUtil.getPersonId());
		if (person == null) {
			log.error("Person did not exist: " + securityUtil.getPersonId());
			return "redirect:/selvbetjening";
		}

		model.addAttribute("settings", settings);
		model.addAttribute("passwordForm", form);

		return "selfservice/change-password";
	}

	@PostMapping("/selvbetjening/skiftkode")
	public String postChangePassword(Model model, @Valid @ModelAttribute("passwordForm") PasswordChangeForm form, BindingResult bindingResult, RedirectAttributes redirectAttributes) {
		if (bindingResult.hasErrors()) {
			model.addAttribute("settings", passwordService.getSettings());

			return "selfservice/change-password";
		}

		Person person = personService.getById(securityUtil.getPersonId());
		if (person == null) {
			log.error("Person did not exist: " + securityUtil.getPersonId());
			return "redirect:/selvbetjening";			
		}

		if (!person.hasNSISUser()) {
			log.warn("Person tried to change password, but no user was associated: " + person.getUuid());
			return "redirect:/selvbetjening";
		}

		try {
			personService.changePassword(person, form.getPassword());
		} catch (NoSuchPaddingException | InvalidKeyException | NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException | UnsupportedEncodingException e) {
			log.error("Could not change password on person", e);
			return "redirect:/selvbetjening";
		}

		redirectAttributes.addFlashAttribute("flashMessage", "Kodeord skiftet");
		
		return "redirect:/selvbetjening";
	}
}
