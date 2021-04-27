package dk.digitalidentity.mvc.admin;

import java.util.List;
import java.util.stream.Collectors;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import dk.digitalidentity.common.config.Constants;
import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.PasswordSetting;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.SessionSetting;
import dk.digitalidentity.common.dao.model.TermsAndConditions;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.DomainService;
import dk.digitalidentity.common.service.PasswordSettingService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.SessionSettingService;
import dk.digitalidentity.common.service.TermsAndConditionsService;
import dk.digitalidentity.mvc.admin.dto.AdministratorDTO;
import dk.digitalidentity.mvc.admin.dto.PasswordConfigurationForm;
import dk.digitalidentity.mvc.admin.dto.SessionConfigurationForm;
import dk.digitalidentity.mvc.admin.dto.TermsAndConditionsDTO;
import dk.digitalidentity.security.RequireAdministrator;
import dk.digitalidentity.security.SecurityUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequireAdministrator
@Controller
public class ConfigurationController {

	@Autowired
	private PasswordSettingService passwordService;

	@Autowired
	private SessionSettingService sessionService;

	@Autowired
	private TermsAndConditionsService termsAndConditionsService;

	@Autowired
	private PersonService personService;

	@Autowired
	private AuditLogger auditLogger;

	@Autowired
	private SecurityUtil securityUtil;
	
	@Autowired
	private DomainService domainService;

	@GetMapping("/admin/konfiguration/sessioner")
	public String getSessionConfiguration(Model model) {
		// Add dummy default form, frontend will load settings by domain.
		SessionConfigurationForm form = new SessionConfigurationForm(180L, 60L);
		model.addAttribute("configForm", form);

		List<Domain> domains = domainService.getAll();
		model.addAttribute("domains", domains);

		return "admin/configure-sessions";
	}

	@PostMapping("/admin/konfiguration/sessioner")
	public String postSessionConfiguration(Model model, @ModelAttribute("configForm") SessionConfigurationForm form, RedirectAttributes redirectAttributes) {
		Person admin = personService.getById(securityUtil.getPersonId());
		if (admin == null) {
			log.error("Could not find admin");
			return "error";
		}

		if (form.getPasswordExpiry() == null || form.getPasswordExpiry() < 10) {
			form.setPasswordExpiry(10L);
		}
		
		if (form.getMfaExpiry() == null || form.getMfaExpiry() < 10) {
			form.setMfaExpiry(10L);
		}

		if (form.getMfaExpiry() > form.getPasswordExpiry()) {
			form.setMfaExpiry(form.getPasswordExpiry());
		}

		SessionSetting settings = sessionService.getSettings(form.getDomainId());
		settings.setPasswordExpiry(form.getPasswordExpiry());
		settings.setMfaExpiry(form.getMfaExpiry());

		sessionService.save(settings);
		auditLogger.changeSessionSettings(settings, admin);

		redirectAttributes.addFlashAttribute("flashMessage", "Session indstillinger opdateret");

		return "redirect:/admin";
	}

	@GetMapping("/admin/konfiguration/password")
	public String getPasswordConfiguration(Model model) {
		// Add dummy default form, frontend will load settings by domain.
		PasswordConfigurationForm form = new PasswordConfigurationForm();
		form.setMinLength(10L);
		form.setBothCapitalAndSmallLetters(true);
		form.setRequireDigits(false);
		form.setRequireSpecialCharacters(false);
		form.setDisallowDanishCharacters(false);
		form.setCacheAdPasswordInterval(1L);
		form.setDomainId(0);
		model.addAttribute("configForm", form);

		List<Domain> domains = domainService.getAll();
		model.addAttribute("domains", domains);

		return "admin/configure-password";
	}
	
	@GetMapping("/admin/konfiguration/links")
	public String getLinksConfiguration(Model model) {
		return "admin/configure-links";
	}

	@PostMapping("/admin/konfiguration/password")
	public String postPasswordConfiguration(Model model, @Valid @ModelAttribute("configForm") PasswordConfigurationForm form, RedirectAttributes redirectAttributes) {
		Person admin = personService.getById(securityUtil.getPersonId());
		if (admin == null) {
			log.error("Could not find admin");
			return "error";
		}

		Domain domain = domainService.getById(form.getDomainId());
		if (domain == null) {
			log.error("Could not find domain");
			return "error";
		}

		PasswordSetting settings = passwordService.getSettings(domain);
		settings.setMinLength(form.getMinLength());
		settings.setBothCapitalAndSmallLetters(form.isBothCapitalAndSmallLetters());
		settings.setRequireDigits(form.isRequireDigits());
		settings.setRequireSpecialCharacters(form.isRequireSpecialCharacters());
		settings.setForceChangePasswordEnabled(form.isForceChangePasswordEnabled());
		settings.setDisallowDanishCharacters(form.isDisallowDanishCharacters());
		settings.setForceChangePasswordInterval(form.getForceChangePasswordInterval() != null ? form.getForceChangePasswordInterval() : 90);
		settings.setDisallowOldPasswords(form.isDisallowOldPasswords());
		settings.setReplicateToAdEnabled(form.isReplicateToAdEnabled());
		settings.setValidateAgainstAdEnabled(form.isValidateAgainstAdEnabled());
		settings.setCacheAdPasswordInterval(form.getCacheAdPasswordInterval() != null ? form.getCacheAdPasswordInterval() : 1);
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

		List<Domain> domains = domainService.getAll();
		model.addAttribute("domains", domains);

		return "admin/administrators-list";
	}

	@GetMapping("/admin/konfiguration/administratorer/tilfoej")
	public String addAdmin(Model model, @RequestParam("type") String type) {
		if (!type.equals(Constants.ROLE_ADMIN) && !type.equals(Constants.ROLE_SUPPORTER) && !type.equals(Constants.ROLE_REGISTRANT)) {
			return "redirect:/admin/konfiguration/administratorer";
		}

		model.addAttribute("type", type);

		return "admin/administrators-add";
	}
}
