package dk.digitalidentity.mvc.admin;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.SessionSetting;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.DomainService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.SessionSettingService;
import dk.digitalidentity.mvc.admin.dto.SessionConfigurationForm;
import dk.digitalidentity.security.RequireAdministrator;
import dk.digitalidentity.security.SecurityUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class ConfigureSessionController {

	@Autowired
	private SessionSettingService sessionService;

	@Autowired
	private PersonService personService;

	@Autowired
	private AuditLogger auditLogger;

	@Autowired
	private SecurityUtil securityUtil;
	
	@Autowired
	private DomainService domainService;

	@Autowired
	private CommonConfiguration commonConfiguration;

	@RequireAdministrator
	@GetMapping("/admin/konfiguration/sessioner")
	public String getSessionConfiguration(Model model) {
		// Add dummy default form, frontend will load settings by domain.
		SessionConfigurationForm form = new SessionConfigurationForm(180L, 60L, false);
		model.addAttribute("configForm", form);

		List<Domain> domains = domainService.getAll();
		model.addAttribute("domains", domains);

		model.addAttribute("nsisSessionPassword", commonConfiguration.getFullServiceIdP().getSessionExpirePassword());
		model.addAttribute("nsisSessionMfa", commonConfiguration.getFullServiceIdP().getSessionExpireMfa());
		
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

	@GetMapping("/rest/admin/settings/session/{domainId}")
	@ResponseBody
	@RequireAdministrator
	public ResponseEntity<SessionConfigurationForm> getSessionSettings(@PathVariable("domainId") long domainId) {
		Domain domain = domainService.getById(domainId);
		if (domain == null) {
			return ResponseEntity.badRequest().build();
		}

		boolean nsisDomain = false;
		if (commonConfiguration.getFullServiceIdP().isEnabled() && !domain.isNonNsis()) {
			nsisDomain = true;
		}

		SessionSetting settings = sessionService.getSettings(domain);

		return ResponseEntity.ok(new SessionConfigurationForm(settings, nsisDomain));
	}
}
