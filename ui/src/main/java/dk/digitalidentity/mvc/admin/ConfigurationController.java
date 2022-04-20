package dk.digitalidentity.mvc.admin;

import java.util.List;
import java.util.stream.Collectors;

import javax.validation.Valid;

import dk.digitalidentity.common.dao.model.Group;
import dk.digitalidentity.common.service.GroupService;
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
import dk.digitalidentity.common.dao.model.PrivacyPolicy;
import dk.digitalidentity.common.dao.model.SessionSetting;
import dk.digitalidentity.common.dao.model.TermsAndConditions;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.DomainService;
import dk.digitalidentity.common.service.PasswordSettingService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.PrivacyPolicyService;
import dk.digitalidentity.common.service.SessionSettingService;
import dk.digitalidentity.common.service.TermsAndConditionsService;
import dk.digitalidentity.mvc.admin.dto.AdministratorDTO;
import dk.digitalidentity.mvc.admin.dto.PasswordConfigurationForm;
import dk.digitalidentity.mvc.admin.dto.PrivacyPolicyDTO;
import dk.digitalidentity.mvc.admin.dto.SessionConfigurationForm;
import dk.digitalidentity.mvc.admin.dto.TermsAndConditionsDTO;
import dk.digitalidentity.security.RequireAdministrator;
import dk.digitalidentity.security.RequireAdministratorOrUserAdministrator;
import dk.digitalidentity.security.SecurityUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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

	@Autowired
	private PrivacyPolicyService privacyPolicyService;

	@Autowired
	private GroupService groupService;

	@RequireAdministrator
	@GetMapping("/admin/konfiguration/sessioner")
	public String getSessionConfiguration(Model model) {
		// Add dummy default form, frontend will load settings by domain.
		SessionConfigurationForm form = new SessionConfigurationForm(180L, 60L);
		model.addAttribute("configForm", form);

		List<Domain> domains = domainService.getAll();
		model.addAttribute("domains", domains);

		return "admin/configure-sessions";
	}

	@RequireAdministrator
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

	@RequireAdministrator
	@GetMapping("/admin/konfiguration/password")
	public String getPasswordConfiguration(Model model) {
		// Add dummy default form, frontend will load settings by domain.
		PasswordConfigurationForm form = new PasswordConfigurationForm();
		form.setMinLength(10L);
		form.setRequireComplexPassword(false);
		form.setRequireLowercaseLetters(true);
		form.setRequireUppercaseLetters(false);
		form.setRequireDigits(false);
		form.setRequireSpecialCharacters(false);
		form.setDisallowDanishCharacters(false);
		form.setValidateAgainstAdEnabled(true);
		form.setDomainId(0);
		form.setChangePasswordOnUsersEnabled(false);
		model.addAttribute("configForm", form);

		List<Domain> domains = domainService.getAll();
		model.addAttribute("domains", domains);

		List<Group> groups = groupService.getAll();
		model.addAttribute("groups", groups);

		return "admin/configure-password";
	}
	
	@RequireAdministrator
	@GetMapping("/admin/konfiguration/links")
	public String getLinksConfiguration(Model model) {
		List<Domain> domains = domainService.getAllParents();
		model.addAttribute("domains", domains);

		return "admin/configure-links";
	}

	@RequireAdministrator
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

		Group group = null;
		if (form.isChangePasswordOnUsersEnabled() && form.getChangePasswordOnUsersGroup() != null) {
			group = groupService.getById(form.getChangePasswordOnUsersGroup());
			if (group == null) {
				log.error("Group Change password on users enabled but no group found from id: " + form.getChangePasswordOnUsersGroup());
				return "error";
			}
		}

		PasswordSetting settings = passwordService.getSettings(domain);
		settings.setMinLength(form.getMinLength());
		settings.setRequireLowercaseLetters(form.isRequireLowercaseLetters());
		settings.setRequireUppercaseLetters(form.isRequireUppercaseLetters());
		settings.setRequireComplexPassword(form.isRequireComplexPassword());
		settings.setRequireDigits(form.isRequireDigits());
		settings.setRequireSpecialCharacters(form.isRequireSpecialCharacters());
		settings.setForceChangePasswordEnabled(form.isForceChangePasswordEnabled());
		settings.setDisallowDanishCharacters(form.isDisallowDanishCharacters());
		settings.setDisallowNameAndUsername(form.isDisallowNameAndUsername());
		settings.setForceChangePasswordInterval(form.getForceChangePasswordInterval() != null ? form.getForceChangePasswordInterval() : 90);
		settings.setAlternativePasswordChangeLink(form.getAlternativePasswordChangeLink());
		settings.setDisallowOldPasswords(form.isDisallowOldPasswords());
		settings.setReplicateToAdEnabled(form.isReplicateToAdEnabled());
		settings.setValidateAgainstAdEnabled(form.isValidateAgainstAdEnabled());
		settings.setMonitoringEnabled(form.isMonitoringEnabled());
		settings.setMonitoringEmail(form.getMonitoringEmail());
		settings.setChangePasswordOnUsersEnabled(group != null);
		settings.setChangePasswordOnUsersGroup(group);

		if (form.isRequireComplexPassword()) {
			settings.setRequireLowercaseLetters(form.isRequireLowercaseLetters());
			settings.setRequireUppercaseLetters(form.isRequireUppercaseLetters());
			settings.setRequireComplexPassword(form.isRequireComplexPassword());
			settings.setRequireDigits(form.isRequireDigits());
		}
		
		passwordService.save(settings);
		auditLogger.changePasswordSettings(settings, admin);

		redirectAttributes.addFlashAttribute("flashMessage", "Passwordregler opdateret");

		return "redirect:/admin";
	}

	@RequireAdministrator
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

	@RequireAdministrator
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

	@RequireAdministrator
	@GetMapping("/admin/konfiguration/privacypolicy")
	public String getPrivacyPolicy(Model model) {
		var privacyPolicy = privacyPolicyService.getPrivacyPolicy();
		if (privacyPolicy == null) {
			log.error("Failed to extract current privacy policy!");
			return "redirect:/admin";
		}

		var privacyPolicyDTO = new PrivacyPolicyDTO();
		privacyPolicyDTO.setContent(privacyPolicy.getContent());

		model.addAttribute("privacyPolicy", privacyPolicyDTO);

		return "admin/configure-privacypolicy";
	}

	@RequireAdministrator
	@PostMapping("/admin/konfiguration/privacypolicy")
	public String savePrivacyPolicy(Model model, PrivacyPolicyDTO privacyPolicyDTO, RedirectAttributes redirectAttributes) {
		var privacyPolicy = privacyPolicyService.getPrivacyPolicy();
		if (privacyPolicy == null) {
			privacyPolicy = new PrivacyPolicy();
		}

		Person admin = personService.getById(securityUtil.getPersonId());
		if (admin == null) {
			log.error("Could not find admin");
			return "error";
		}

		privacyPolicy.setContent(privacyPolicyDTO.getContent());
		privacyPolicyService.save(privacyPolicy);
		auditLogger.changePrivacyPolicy(privacyPolicy, admin);

		redirectAttributes.addFlashAttribute("flashMessage", "Privatlivspolitik opdateret");

		return "redirect:/admin";
	}

	@RequireAdministratorOrUserAdministrator
	@GetMapping("/admin/konfiguration/administratorer")
	public String getAdministrators(Model model) {
		long loggedInPersonId = securityUtil.getPersonId();
		
		List<AdministratorDTO> admins = personService.getAllAdminsAndSupporters().stream()
				.map(a -> new AdministratorDTO(a, loggedInPersonId == a.getId()))
				.collect(Collectors.toList());

		model.addAttribute("admins", admins);

		List<Domain> domains = domainService.getAllParents();
		model.addAttribute("domains", domains);

		return "admin/administrators-list";
	}

	@RequireAdministratorOrUserAdministrator
	@GetMapping("/admin/konfiguration/administratorer/tilfoej")
	public String addAdmin(Model model, @RequestParam("type") String type) {
		if (!type.equals(Constants.ROLE_ADMIN) && !type.equals(Constants.ROLE_SUPPORTER) && !type.equals(Constants.ROLE_REGISTRANT) && !type.equals(Constants.ROLE_SERVICE_PROVIDER_ADMIN) && !type.equals(Constants.ROLE_USER_ADMIN)) {
			return "redirect:/admin/konfiguration/administratorer";
		}
		
		model.addAttribute("type", type);
		model.addAttribute("loggedInUserId", securityUtil.getPersonId());
		List<Domain> domains = domainService.getAllParents();
		model.addAttribute("domains", domains);

		return "admin/administrators-add";
	}
}
