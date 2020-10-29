package dk.digitalidentity.mvc.admin;

import dk.digitalidentity.common.config.Constants;
import dk.digitalidentity.common.dao.model.PasswordSetting;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.TermsAndConditions;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.PasswordSettingService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.TermsAndConditionsService;
import dk.digitalidentity.mvc.admin.dto.AdministratorDTO;
import dk.digitalidentity.mvc.admin.dto.PasswordConfigurationForm;
import dk.digitalidentity.mvc.admin.dto.TermsAndConditionsDTO;
import dk.digitalidentity.security.RequireAdministrator;
import dk.digitalidentity.security.SecurityUtil;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@RequireAdministrator
@Controller
public class ConfigurationController {

	@Autowired
	private PasswordSettingService passwordService;

	@Autowired
	private TermsAndConditionsService termsAndConditionsService;

	@Autowired
	private PersonService personService;

	@Autowired
	private AuditLogger auditLogger;

	@Autowired
	private SecurityUtil securityUtil;

	@GetMapping("/admin/konfiguration/password")
	public String getPasswordConfiguration(Model model) {
		PasswordConfigurationForm form = new PasswordConfigurationForm(passwordService.getSettings());

		model.addAttribute("configForm", form);

		return "admin/configure-password";
	}

	@PostMapping("/admin/konfiguration/password")
	public String postPasswordConfiguration(Model model, @Valid @ModelAttribute("configForm") PasswordConfigurationForm form, RedirectAttributes redirectAttributes) {
		Person admin = personService.getById(securityUtil.getPersonId());
		if (admin == null) {
			log.error("Could not find admin");
			return "error";
		}

		PasswordSetting settings = passwordService.getSettings();
		settings.setMinLength(form.getMinLength());
		settings.setBothCapitalAndSmallLetters(form.isBothCapitalAndSmallLetters());
		settings.setRequireDigits(form.isRequireDigits());
		settings.setRequireSpecialCharacters(form.isRequireSpecialCharacters());
		settings.setForceChangePasswordEnabled(form.isForceChangePasswordEnabled());
		settings.setForceChangePasswordInterval(form.getForceChangePasswordInterval());
		settings.setReplicateToAdEnabled(form.isReplicateToAdEnabled());
		settings.setValidateAgainstAdEnabled(form.isValidateAgainstAdEnabled());
		settings.setMonitoringEnabled(form.isMonitoringEnabled());
		settings.setMonitoringEmail(form.getMonitoringEmail());
		
		passwordService.save(settings);
		auditLogger.changePasswordSettings(settings, admin);

		redirectAttributes.addFlashAttribute("flashMessage", "Passwordregler opdateret");

		return "redirect:/admin";
	}

	@GetMapping("/admin/konfiguration/vilkaar")
	public String getTermsConfiguration(Model model) {
		TermsAndConditions termsAndConditions = termsAndConditionsService.getTermsAndConditions();
		if (termsAndConditions == null) {
			log.error("Failed to extract current terms and conditions!");
			return "redirect:/admin";
		}

		TermsAndConditionsDTO termsAndConditionsDTO = new TermsAndConditionsDTO();
		termsAndConditionsDTO.setContent(termsAndConditions.getContent());

		model.addAttribute("termsAndConditions", termsAndConditionsDTO);

		return "admin/configure-terms";
	}

	@PostMapping("/admin/konfiguration/vilkaar")
	public String saveTermsConfiguration(Model model, TermsAndConditionsDTO termsAndConditionsDTO, RedirectAttributes redirectAttributes) {
		TermsAndConditions terms = termsAndConditionsService.getTermsAndConditions();
		if (terms == null) {
			terms = new TermsAndConditions();
		}

		Person admin = personService.getById(securityUtil.getPersonId());
		if (admin == null) {
			log.error("Could not find admin");
			return "error";
		}

		terms.setContent(termsAndConditionsDTO.getContent());
		termsAndConditionsService.save(terms);
		auditLogger.changeTerms(terms, admin);

		redirectAttributes.addFlashAttribute("flashMessage", "Anvendelsesvilkår opdateret");

		return "redirect:/admin";
	}

	@GetMapping("/admin/konfiguration/administratorer")
	public String getAdministrators(Model model) {
		List<AdministratorDTO> admins = personService.getAllAdminsAndSupporters().stream()
				.map(a -> new AdministratorDTO(a))
				.collect(Collectors.toList());

		model.addAttribute("admins", admins);

		return "admin/administrators-list";
	}

	@GetMapping("/admin/konfiguration/administratorer/tilfoej")
	public String addAdmin(Model model, @RequestParam("type") String type) {
		if (!type.equals(Constants.ROLE_ADMIN) && !type.equals(Constants.ROLE_SUPPORTER)) {
			return "redirect:/admin/konfiguration/administratorer";
		}

		model.addAttribute("type", type);

		return "admin/administrators-add";
	}
}
