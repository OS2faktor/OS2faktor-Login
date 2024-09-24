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
import javax.servlet.http.HttpServletRequest;

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
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.enums.ChangePasswordResult;
import dk.digitalidentity.common.service.model.ADPasswordResponse.ADPasswordStatus;
import dk.digitalidentity.service.PasswordService;
import dk.digitalidentity.service.SessionHelper;
import dk.digitalidentity.service.model.enums.PasswordValidationResult;
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
		List<Person> users = personService.getBySamaccountName(samAcc);
		model.addAttribute("redirectUrl", redirectUrl);
		
		if (users.isEmpty()) {
			// Pretend that there is users
			model.addAttribute("personId", 0);

			PasswordSetting passwordSettings = passwordSettingService.getAllSettings().stream().findFirst().orElse(null);
			model.addAttribute("settings", passwordSettings);

			return new ModelAndView("changePasswordNonFlow/change-password-non-flow-next", model.asMap());
		}
		else if (users.size() == 1) {
			Person person = users.stream().findFirst().get();
			model.addAttribute("personId", person.getId());

			PasswordSetting passwordSettings = passwordSettingService.getSettingsCached(person.getDomain());
			model.addAttribute("settings", passwordSettings);

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
			PasswordSetting settingsCached = passwordSettingService.getSettingsCached(person.getDomain());
			model.addAttribute("settings", settingsCached);
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
		ChangePasswordResult result = passwordSettingService.validatePasswordRules(person, newPW, true);
		if (!result.equals(ChangePasswordResult.OK)) {
			model.addAttribute("personId", personId);
			model.addAttribute("failureReason", result.getMessage());

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
				case VALID_CACHE:
					log.error("Should not be able to validate via cache (CacheStrategy NoCache), something broke");
					break;
				case INVALID:
					failureReason = "Kodeord er forkert";
					personService.badPasswordAttempt(person, false);
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

		model.addAttribute("settings", passwordSettingService.getSettingsCached(person.getDomain()));		
		model.addAttribute("personId", personId);
		model.addAttribute("failureReason", failureReason);

		return new ModelAndView("changePasswordNonFlow/change-password-non-flow-next", model.asMap());
	}

	// check if there is a limit of how many times a person can change password a day and then if that limit is exceeded
	private boolean changedPasswordTooManyTimes(Person person) {
		PasswordSetting settings = passwordSettingService.getSettings(person.getDomain());
		if (settings.isMaxPasswordChangesPrDayEnabled() && person.getDailyPasswordChangeCounter() >= settings.getMaxPasswordChangesPrDay()) {
			return true;
		}

		return false;
	}
}
