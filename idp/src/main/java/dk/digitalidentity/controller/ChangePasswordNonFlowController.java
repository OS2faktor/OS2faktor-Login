package dk.digitalidentity.controller;

import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import javax.annotation.Nullable;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.PasswordSetting;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.service.CmsMessageBundle;
import dk.digitalidentity.common.service.PasswordSettingService;
import dk.digitalidentity.common.service.PasswordValidationService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.enums.ChangePasswordResult;
import dk.digitalidentity.common.service.model.ADPasswordResponse.ADPasswordStatus;
import dk.digitalidentity.service.PasswordService;
import dk.digitalidentity.service.SessionHelper;
import dk.digitalidentity.service.model.enums.PasswordValidationResult;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class ChangePasswordNonFlowController {

	@Autowired
	private SessionHelper sessionHelper;

	@Autowired
	private PersonService personService;

	@Autowired
	private PasswordSettingService passwordSettingService;
	
	@Autowired
	private PasswordValidationService passwordValidationService;

	@Autowired
	private PasswordService passwordService;
	
	@Autowired
	private CmsMessageBundle messageBundle;
	
	@Autowired
	private CommonConfiguration configuration;

	@GetMapping("/change-password")
	public ModelAndView changePasswordNonFlow(Model model, HttpServletRequest request) {
		if (!configuration.getPasswordSoonExpire().isChangePasswordPageEnabled()) {
			return new ModelAndView("redirect:/");
		}

		String redirectUrl = "";
		String queryString = request.getQueryString();
		if (queryString != null) {
			String[] encodedParameters = queryString.split("&");

			for (String param : encodedParameters) {
				String[] keyValuePair = param.split("=");

				// Find RedirectUrl if present, otherwise set empty string
				if ("redirectUrl".equalsIgnoreCase(keyValuePair[0])) {
					redirectUrl = keyValuePair[1];
				}
			}
		}
		model.addAttribute("redirectUrl", redirectUrl);

		return new ModelAndView("changePasswordNonFlow/change-password-non-flow", model.asMap());
	}

	@PostMapping("/change-password")
	public ModelAndView getNewPassword(Model model, HttpServletRequest request, @RequestParam(name = "redirectUrl") String redirectUrl, @RequestParam(name = "username") String samAcc) {
        // allow users to input username with domain, for now disregard domain
        if (samAcc.contains("@")) {
            String[] split = samAcc.split("@");
            if (split.length > 0) {
            	samAcc = split[0];
            }
            else {
            	// if they only supply @ as the username, the username is empty
            	samAcc = "";
            }
        }

		List<Person> users = personService.getBySamaccountName(samAcc);
		model.addAttribute("redirectUrl", redirectUrl);
		
		if (users.isEmpty()) {
			// Pretend that there is users
			model.addAttribute("personId", 0);

			PasswordSetting passwordSettings = passwordSettingService.getAllSettings().stream().findFirst().orElse(null);
			model.addAttribute("settings", passwordSettings);
			model.addAttribute("disallowNameAndUsernameContent", "");

			log.warn("POST on /change-password but no person on session");
			
			return new ModelAndView("changePasswordNonFlow/change-password-non-flow-next", model.asMap());
		}
		else if (users.size() == 1) {
			Person person = users.stream().findFirst().get();
			model.addAttribute("personId", person.getId());

			PasswordSetting passwordSettings = passwordSettingService.getSettingsCached(passwordSettingService.getSettingsDomainForPerson(person));
			model.addAttribute("settings", passwordSettings);
			model.addAttribute("disallowNameAndUsernameContent", passwordSettingService.getDisallowedNames(person));

			return new ModelAndView("changePasswordNonFlow/change-password-non-flow-next", model.asMap());
		}

		model.addAttribute("people", users);

		return new ModelAndView("changePasswordNonFlow/change-password-non-flow-select-user", model.asMap());
	}

	@GetMapping("/change-password-next")
	public ModelAndView setNewPassword(Model model, @RequestParam(name = "redirectUrl") String redirectUrl, @Nullable @RequestParam long personId) {
		model.addAttribute("personId", personId);
		model.addAttribute("redirectUrl", redirectUrl);

		Person person = personService.getById(personId);
		if (person != null) {
			PasswordSetting settingsCached = passwordSettingService.getSettingsCached(passwordSettingService.getSettingsDomainForPerson(person));
			model.addAttribute("settings", settingsCached);
			model.addAttribute("disallowNameAndUsernameContent", passwordSettingService.getDisallowedNames(person));
		}

		return new ModelAndView("changePasswordNonFlow/change-password-non-flow-next", model.asMap());
	}

	@PostMapping("/change-password-next")
	public ModelAndView setNewPassword(Model model, @RequestParam(name = "redirectUrl") String redirectUrl, @RequestParam String oldPW, @RequestParam String newPW, @RequestParam long personId) throws InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException, UnsupportedEncodingException, NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {
		Person person = personService.getById(personId);
		if (person == null) {
			return new ModelAndView("redirect:/change-password");
		}

		String failureReason = "";
		ChangePasswordResult result = passwordValidationService.validatePasswordRules(person, newPW, true, false);
		if (!result.equals(ChangePasswordResult.OK)) {
			model.addAttribute("personId", personId);
			model.addAttribute("failureReason", result.getMessage());
			model.addAttribute("disallowNameAndUsernameContent", passwordSettingService.getDisallowedNames(person));

			return new ModelAndView("changePasswordNonFlow/change-password-non-flow-next", model.asMap());
		}

		if (changedPasswordTooManyTimes(person)) {
			failureReason = "Antal daglige skift af kodeord er nået";
		}
		else {
			sessionHelper.setPasswordLevel(null);
			PasswordValidationResult pwvr = passwordService.validatePassword(oldPW, person);
			
			switch (pwvr) {
				case VALID:
				case VALID_EXPIRED: // This is ok, you CAN change your expired password for a new one
				case VALID_BUT_BAD_PASWORD: // this is also okay, you CAN (and SHOULD) change a bad password
					ADPasswordStatus status = personService.changePassword(personService.getById(personId), newPW);

					switch (status) {
						case NOOP:
						case OK:
						case TIMEOUT:
							model.addAttribute("redirectUrl", redirectUrl);
							
							return new ModelAndView("changePassword/change-password-success", model.asMap());
						case FAILURE:
						case TECHNICAL_ERROR:
							failureReason = "Teknisk fejl, prøv igen. Forsætter fejlen så kontakt support";
							break;
						case INSUFFICIENT_PERMISSION:
							failureReason = messageBundle.getText("cms.changePassword.insufficient-permission");
							break;
					}
					break;
				case INVALID:
					failureReason = "Kodeord er forkert";
					personService.badPasswordAttempt(person, false);
					break;
				case INVALID_BAD_PASSWORD:
					failureReason = "Det eksisterende kodeord er ugyldigt, og MitID skal anvendes for at vælge et nyt kodeord";
					break;
				case LOCKED:
					failureReason = "Kontoen er låst";
					break;
				case INSUFFICIENT_PERMISSION:
					failureReason = messageBundle.getText("cms.changePassword.insufficient-permission");
					break;
				case TECHNICAL_ERROR:
					failureReason = "Teknisk fejl, prøv igen. Forsætter fejlen så kontakt support";
					break;
			}
		}

		model.addAttribute("settings", passwordSettingService.getSettingsCached(passwordSettingService.getSettingsDomainForPerson(person)));		
		model.addAttribute("personId", personId);
		model.addAttribute("failureReason", failureReason);
		model.addAttribute("disallowNameAndUsernameContent", passwordSettingService.getDisallowedNames(person));

		return new ModelAndView("changePasswordNonFlow/change-password-non-flow-next", model.asMap());
	}

	// check if there is a limit of how many times a person can change password a day and then if that limit is exceeded
	private boolean changedPasswordTooManyTimes(Person person) {
		PasswordSetting settings = passwordSettingService.getSettingsCached(passwordSettingService.getSettingsDomainForPerson(person));
		if (settings.isMaxPasswordChangesPrDayEnabled() && person.getDailyPasswordChangeCounter() >= settings.getMaxPasswordChangesPrDay()) {
			return true;
		}

		return false;
	}
}
