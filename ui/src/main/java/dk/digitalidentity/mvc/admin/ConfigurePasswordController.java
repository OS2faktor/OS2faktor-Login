package dk.digitalidentity.mvc.admin;

import java.util.List;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.datatables.mapping.DataTablesInput;
import org.springframework.data.jpa.datatables.mapping.DataTablesOutput;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.BadPassword;
import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.Group;
import dk.digitalidentity.common.dao.model.PasswordSetting;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.SettingsKey;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.BadPasswordService;
import dk.digitalidentity.common.service.DomainService;
import dk.digitalidentity.common.service.GroupService;
import dk.digitalidentity.common.service.PasswordSettingService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.SettingService;
import dk.digitalidentity.datatables.BadPasswordDatatableDao;
import dk.digitalidentity.mvc.admin.dto.PasswordConfigurationForm;
import dk.digitalidentity.security.RequireAdministrator;
import dk.digitalidentity.security.SecurityUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class ConfigurePasswordController {

	@Autowired
	private PasswordSettingService passwordService;

	@Autowired
	private PersonService personService;

	@Autowired
	private AuditLogger auditLogger;

	@Autowired
	private SecurityUtil securityUtil;
	
	@Autowired
	private DomainService domainService;

	@Autowired
	private GroupService groupService;
	
	@Autowired
	private CommonConfiguration commonConfiguration;

	@Autowired
	private SettingService settingService;

	@Autowired
	private BadPasswordDatatableDao badPasswordDatatableDao;

	@Autowired
	private BadPasswordService badPasswordService;

	// runs on startup and migrates password policies if/when fullServiceIdP is enabled
	@EventListener(ApplicationReadyEvent.class)
	public void migrateFullServiceIdP() {
		if (commonConfiguration.getFullServiceIdP().isEnabled() && !settingService.getBoolean(SettingsKey.FULL_SERVICE_IDP_MIGRATED)) {

			// migrate password policies for all non-non-nsis domains
			for (Domain domain : domainService.getAll()) {
				if (!domain.isNonNsis()) {
					PasswordSetting settings = passwordService.getSettings(domain);

					if (settings.getMinLength() < commonConfiguration.getFullServiceIdP().getMinimumPasswordLength()) {
						settings.setMinLength(commonConfiguration.getFullServiceIdP().getMinimumPasswordLength());
						
						passwordService.save(settings);
						auditLogger.changePasswordSettings(settings, null);
					}
				}
			}
			
			settingService.setBoolean(SettingsKey.FULL_SERVICE_IDP_MIGRATED, true);
		}
	}

	@RequireAdministrator
	@GetMapping("/admin/konfiguration/password")
	public String getPasswordConfiguration(Model model) {
		
		// add dummy default form, frontend will load settings by domain.
		PasswordConfigurationForm form = new PasswordConfigurationForm();
		form.setMinMinLength(4L);
		form.setMinLength(8L);
		form.setRequireComplexPassword(false);
		form.setRequireLowercaseLetters(true);
		form.setRequireUppercaseLetters(false);
		form.setRequireDigits(false);
		form.setRequireSpecialCharacters(false);
		form.setDisallowDanishCharacters(false);
		form.setPreventBadPasswords(true);
		form.setCheckLeakedPasswords(true);
		form.setDomainId(0);
		model.addAttribute("configForm", form);

		List<Domain> domains = domainService.getAll();
		model.addAttribute("domains", domains);

		List<Group> groups = groupService.getAll();
		model.addAttribute("groups", groups);

		model.addAttribute("fullServiceIdP", commonConfiguration.getFullServiceIdP().isEnabled());
		
		if (commonConfiguration.getFullServiceIdP().isEnabled()) {
			form.setMinLength(commonConfiguration.getFullServiceIdP().getMinimumPasswordLength());
			form.setMinMinLength(commonConfiguration.getFullServiceIdP().getMinimumPasswordLength());
		}
		
		return "admin/configure-password";
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
		
		Group canNotChangePasswordGroup = null;
		if (form.isCanNotChangePasswordEnabled() && form.getCanNotChangePasswordGroup() != null) {
			canNotChangePasswordGroup = groupService.getById(form.getCanNotChangePasswordGroup());
			if (canNotChangePasswordGroup == null) {
				log.error("Can not change password enabled but no group found from id: " + form.getCanNotChangePasswordGroup());
				return "error";
			}
		}

		PasswordSetting settings = passwordService.getSettings(domain);
		settings.setMinLength(form.getMinLength());
		settings.setMaxLength(form.getMaxLength());
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
		settings.setOldPasswordNumber(form.getOldPasswordNumber() != null ? form.getOldPasswordNumber() : 10);
		settings.setMonitoringEnabled(form.isMonitoringEnabled());
		settings.setMonitoringEmail(form.getMonitoringEmail());
		settings.setCheckLeakedPasswords(form.isCheckLeakedPasswords());
		settings.setTriesBeforeLockNumber(form.getTriesBeforeLockNumber() != null ? form.getTriesBeforeLockNumber() : 5);
		settings.setLockedMinutes(form.getLockedMinutes() != null ? form.getLockedMinutes() : 5);
		settings.setMaxPasswordChangesPrDayEnabled(form.isMaxPasswordChangesPrDayEnabled());
		settings.setMaxPasswordChangesPrDay(form.getMaxPasswordChangesPrDay() != null ? form.getMaxPasswordChangesPrDay() : 1);
		settings.setCanNotChangePasswordEnabled(canNotChangePasswordGroup != null);
		settings.setCanNotChangePasswordGroup(canNotChangePasswordGroup);
		settings.setPreventBadPasswords(form.isPreventBadPasswords());
		settings.setSpecificSpecialCharactersEnabled(form.isSpecificSpecialCharactersEnabled());
		settings.setAllowedSpecialCharacters(form.isSpecificSpecialCharactersEnabled() ? form.getAllowedSpecialCharacters() : null);
		
		if (form.isRequireComplexPassword()) {
			settings.setRequireLowercaseLetters(true);
			settings.setRequireUppercaseLetters(true);
			settings.setRequireComplexPassword(true);
			settings.setRequireDigits(true);
			settings.setDisallowNameAndUsername(true);
		}
		
		if (commonConfiguration.getFullServiceIdP().isEnabled() && !domain.isNonNsis()) {
			if (settings.getMinLength() < commonConfiguration.getFullServiceIdP().getMinimumPasswordLength()) {
				settings.setMinLength(commonConfiguration.getFullServiceIdP().getMinimumPasswordLength());				
			}
			
			// TODO: probably should be moved to commonConfiguration so we CAN change them
			// these values are hardcoded for FullService IdP's
			settings.setTriesBeforeLockNumber(5L);
			settings.setLockedMinutes(5L);
		}

		passwordService.save(settings);
		auditLogger.changePasswordSettings(settings, admin);

		redirectAttributes.addFlashAttribute("flashMessage", "Passwordregler opdateret");

		return "redirect:/admin";
	}
	
	@GetMapping("/rest/admin/settings/password/{domainId}")
	@ResponseBody
	@RequireAdministrator
	public ResponseEntity<PasswordConfigurationForm> getPasswordSettings(@PathVariable("domainId") long domainId) {
		Domain domain = domainService.getById(domainId);
		if (domain == null) {
			return ResponseEntity.badRequest().build();
		}

		PasswordSetting settings = passwordService.getSettings(domain);
		PasswordConfigurationForm form = new PasswordConfigurationForm(settings);
		
		if (commonConfiguration.getFullServiceIdP().isEnabled() && !domain.isNonNsis()) {
			// should not happen, but better safe than sorry
			if (form.getMinLength() < commonConfiguration.getFullServiceIdP().getMinimumPasswordLength()) {
				form.setMinLength(commonConfiguration.getFullServiceIdP().getMinimumPasswordLength());
			}
			
			form.setMinMinLength(commonConfiguration.getFullServiceIdP().getMinimumPasswordLength());
		}

		// only show these for parent domains (and only those that are not standalone)
		form.setShowAdSettings(domain.getParent() == null && !domain.isStandalone());
		
		return ResponseEntity.ok(form);
	}

	@RequireAdministrator
	@GetMapping("/admin/konfiguration/badpassword")
	public String getbadPasswords(Model model) {
		return "admin/configure-bad-password";
	}

	@RequireAdministrator
	@PostMapping("/rest/admin/konfiguration/badpassword")
	@ResponseBody
	public DataTablesOutput<BadPassword> getBadPasswords(@RequestBody DataTablesInput input) {
		return badPasswordDatatableDao.findAll(input);
	}
	
	@RequireAdministrator
	@PostMapping("/rest/admin/konfiguration/badPassword/add")
	public ResponseEntity<?> addBadPassword(@RequestBody String badPassword) {
		if (badPasswordService.findByPassword(badPassword).size() > 0) {
			return new ResponseEntity<>(HttpStatus.CONFLICT);
		}

		BadPassword bp = new BadPassword();
		bp.setPassword(badPassword);
		badPasswordService.save(bp);

		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	@RequireAdministrator
	@PostMapping("/rest/admin/konfiguration/badPassword/remove/{id}")
	public ResponseEntity<?> removeBadPassword(@PathVariable("id") long id){
		badPasswordService.delete(id);

		return new ResponseEntity<>(HttpStatus.OK);
	}
}
