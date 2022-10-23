package dk.digitalidentity.mvc.admin;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.config.Constants;
import dk.digitalidentity.common.config.FeatureDocumentation;
import dk.digitalidentity.common.config.RoleSettingDTO;
import dk.digitalidentity.common.config.RoleSettingType;
import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.Group;
import dk.digitalidentity.common.dao.model.PasswordSetting;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.PrivacyPolicy;
import dk.digitalidentity.common.dao.model.SessionSetting;
import dk.digitalidentity.common.dao.model.TermsAndConditions;
import dk.digitalidentity.common.dao.model.WindowCredentialProviderClient;
import dk.digitalidentity.common.dao.model.enums.LogWatchSettingKey;
import dk.digitalidentity.common.dao.model.enums.SchoolClassType;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.DomainService;
import dk.digitalidentity.common.service.GroupService;
import dk.digitalidentity.common.service.LogWatchSettingService;
import dk.digitalidentity.common.service.PasswordSettingService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.PrivacyPolicyService;
import dk.digitalidentity.common.service.SessionSettingService;
import dk.digitalidentity.common.service.TermsAndConditionsService;
import dk.digitalidentity.common.service.WindowCredentialProviderClientService;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.mvc.admin.dto.AdministratorDTO;
import dk.digitalidentity.mvc.admin.dto.FeatureDTO;
import dk.digitalidentity.mvc.admin.dto.LogWatchSettingsDto;
import dk.digitalidentity.mvc.admin.dto.PasswordConfigurationForm;
import dk.digitalidentity.mvc.admin.dto.PrivacyPolicyDTO;
import dk.digitalidentity.mvc.admin.dto.SessionConfigurationForm;
import dk.digitalidentity.mvc.admin.dto.TermsAndConditionsDTO;
import dk.digitalidentity.mvc.admin.dto.WindowCredentialProviderClientDTO;
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
	
	@Autowired
	private OS2faktorConfiguration os2faktorConfiguration;
	
	@Autowired
	private CommonConfiguration commonConfiguration;

	@Autowired
	private WindowCredentialProviderClientService clientService;
	
	@Autowired
	private LogWatchSettingService logWatchSettingService;

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
		form.setMinLength(8L);
		form.setRequireComplexPassword(false);
		form.setRequireLowercaseLetters(true);
		form.setRequireUppercaseLetters(false);
		form.setRequireDigits(false);
		form.setRequireSpecialCharacters(false);
		form.setDisallowDanishCharacters(false);
		form.setValidateAgainstAdEnabled(true);
		form.setPreventBadPasswords(true);
		form.setDomainId(0);
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
		settings.setReplicateToAdEnabled(form.isReplicateToAdEnabled());
		settings.setValidateAgainstAdEnabled(form.isValidateAgainstAdEnabled());
		settings.setMonitoringEnabled(form.isMonitoringEnabled());
		settings.setMonitoringEmail(form.getMonitoringEmail());
		settings.setTriesBeforeLockNumber(form.getTriesBeforeLockNumber() != null ? form.getTriesBeforeLockNumber() : 5);
		settings.setLockedMinutes(form.getLockedMinutes() != null ? form.getLockedMinutes() : 5);
		settings.setMaxPasswordChangesPrDayEnabled(form.isMaxPasswordChangesPrDayEnabled());
		settings.setMaxPasswordChangesPrDay(form.getMaxPasswordChangesPrDay() != null ? form.getMaxPasswordChangesPrDay() : 1);
		settings.setCanNotChangePasswordEnabled(canNotChangePasswordGroup != null);
		settings.setCanNotChangePasswordGroup(canNotChangePasswordGroup);
		settings.setPreventBadPasswords(form.isPreventBadPasswords());
		
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

		if (termsAndConditionsDTO.isMustApprove()) {
			terms.setMustApproveTts(LocalDateTime.now());
		}

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
	
	@RequireAdministrator
	@GetMapping("/admin/konfiguration/features")
	public String getFeatureList(Model model) {
		List<FeatureDTO> features = new ArrayList<>();
		getFields(os2faktorConfiguration.getClass().getDeclaredFields(), features, os2faktorConfiguration);
		getFields(commonConfiguration.getClass().getDeclaredFields(), features, commonConfiguration);
		
		model.addAttribute("features", features);
		
		List<RoleSettingDTO> settings = commonConfiguration.getStilStudent().getRoleSettings();
		model.addAttribute("roleSettings", transformFilterMessage(settings));
		
		return "admin/configure-features";
	}
		
	private List<RoleSettingDTO> transformFilterMessage(List<RoleSettingDTO> settings) {
		List<RoleSettingDTO> newSettingList = new ArrayList<>();
		
		for (RoleSettingDTO dto : settings) {
			RoleSettingDTO newSetting = new RoleSettingDTO();
			newSetting.setRole(dto.getRole());
			newSetting.setType(dto.getType());
			
			String filterString = dto.getFilter();
			if (dto.getType().equals(RoleSettingType.CAN_CHANGE_PASSWORD_ON_GROUP_MATCH)) {
				List<String> filterClassTypes = Arrays.asList(dto.getFilter().split(","));
				filterString = "";
				
				for (String filter : filterClassTypes) {
					SchoolClassType type = SchoolClassType.valueOf(filter);
					if (type != null) {
						filterString += type.getMessage() + ", ";
					}
				}
				
				filterString = filterString.substring(0, filterString.length()-2);
			}
			
			newSetting.setFilter(filterString);
			newSettingList.add(newSetting);
		}
		
		return newSettingList;
	}

	private void getFields(Field[] fields, List<FeatureDTO> features, Object object) {
	    try {
	        for (Field field : fields) {
	            field.setAccessible(true);

	            if (field.isAnnotationPresent(FeatureDocumentation.class) && field.getType().equals(boolean.class)) {
	            	FeatureDocumentation annotation = field.getAnnotation(FeatureDocumentation.class);

	            	FeatureDTO feature = new FeatureDTO();
	            	feature.setDescription(annotation.description());
	            	feature.setName(annotation.name());
	            	feature.setEnabled(field.getBoolean(object));
	            	features.add(feature);
			    }
	            else {
	            	
	            	// don't call getFields if field is enum - will cause endless loop
			    	if (field.getType().getPackageName().startsWith("dk.") && !(field.getType() instanceof Class && ((Class<?>)field.getType()).isEnum())) {
			    		getFields(field.getType().getDeclaredFields(), features, field.get(object));
			    	}
			    }
	        }
	    }
	    catch (IllegalArgumentException e) {
	        log.error("A method has been passed a wrong argument in the getFields method for feature documentation.");
	    }
	    catch (SecurityException e) {
	    	log.error("Security violation in the getFields method for feature documentation");
		}
	    catch (IllegalAccessException e) {
			log.error("tries to acces a field or method, that is not allowed from the getFields method for feature documentation");
		}
	}

	@RequireAdministrator
	@GetMapping("/admin/konfiguration/wcp")
	public String getWindowsCreadentialProvicerClients(Model model) {
		List<WindowCredentialProviderClient> clients = clientService.getAll();
		List<WindowCredentialProviderClientDTO> clientsDTO = clients.stream().map(WindowCredentialProviderClientDTO::new).collect(Collectors.toList());

		model.addAttribute("clients", clientsDTO);
		
		return "admin/configure-wcp";
	}
	
	@RequireAdministrator
	@GetMapping("/admin/konfiguration/logovervaagning")
	public String getLogWatch(Model model) {
		LogWatchSettingsDto settings = getLogWatchSettingsDto();
		model.addAttribute("settings", settings);
		
		return "admin/configure-logwatch";
	}
	
	@RequireAdministrator
	@PostMapping("/admin/konfiguration/logovervaagning")
	public String postLogWatch(Model model, LogWatchSettingsDto logWatchSettingsDto, RedirectAttributes redirectAttributes) {
		Person admin = personService.getById(securityUtil.getPersonId());
		if (admin == null) {
			log.error("Could not find admin");
			return "error";
		}
		
		if (logWatchSettingsDto.isEnabled()) {
			String regex = "^(.+)@(.+)$";
			Pattern pattern = Pattern.compile(regex);
			Matcher matcher = pattern.matcher(logWatchSettingsDto.getAlarmEmail());
			
			if (!matcher.matches()) {
				model.addAttribute("settings", logWatchSettingsDto);
				model.addAttribute("emailError", true);
				
				return "admin/configure-logwatch";
			}
		}
		
		logWatchSettingService.setBooleanValue(LogWatchSettingKey.LOG_WATCH_ENABLED, logWatchSettingsDto.isEnabled());
		logWatchSettingService.setStringValue(LogWatchSettingKey.ALARM_EMAIL, logWatchSettingsDto.getAlarmEmail());
		logWatchSettingService.setBooleanValue(LogWatchSettingKey.TWO_COUNTRIES_ONE_HOUR_ENABLED, logWatchSettingsDto.isTwoCountriesOneHourEnabled());
		logWatchSettingService.setBooleanValue(LogWatchSettingKey.PERSON_DEAD_OR_DISENFRANCHISED_ENABLED, logWatchSettingsDto.isPersonDeadOrIncapacitatedEnabled());
		logWatchSettingService.setBooleanValue(LogWatchSettingKey.TOO_MANY_ACCOUNTS_LOCKED_BY_ADMIN_TODAY_ENABLED, logWatchSettingsDto.isTooManyAccountsLockedByAdminTodayEnabled());
		logWatchSettingService.setLongValue(LogWatchSettingKey.TOO_MANY_ACCOUNTS_LOCKED_BY_ADMIN_TODAY_LIMIT, logWatchSettingsDto.getTooManyAccountsLockedByAdminTodayLimit());
		logWatchSettingService.setBooleanValue(LogWatchSettingKey.TOO_MANY_TIME_LOCKED_ACCOUNTS_ENABLED, logWatchSettingsDto.isTooManyTimeLockedAccountsEnabled());
		logWatchSettingService.setLongValue(LogWatchSettingKey.TOO_MANY_TIME_LOCKED_ACCOUNTS_LIMIT, logWatchSettingsDto.getTooManyTimeLockedAccountsLimit());
		logWatchSettingService.setBooleanValue(LogWatchSettingKey.TOO_MANY_WRONG_PASSWORDS_ENABLED, logWatchSettingsDto.isTooManyWrongPasswordsEnabled());
		logWatchSettingService.setLongValue(LogWatchSettingKey.TOO_MANY_WRONG_PASSWORDS_LIMIT, logWatchSettingsDto.getTooManyWrongPasswordsLimit());
				
		redirectAttributes.addFlashAttribute("flashMessage", "Indstillinger for overvågning af logs opdateret");
		
		return "redirect:/admin";
	}

	private LogWatchSettingsDto getLogWatchSettingsDto() {
		LogWatchSettingsDto settings = new LogWatchSettingsDto();
		settings.setEnabled(logWatchSettingService.getBooleanWithDefaultFalse(LogWatchSettingKey.LOG_WATCH_ENABLED));
		settings.setAlarmEmail(logWatchSettingService.getString(LogWatchSettingKey.ALARM_EMAIL));
		settings.setTwoCountriesOneHourEnabled(logWatchSettingService.getBooleanWithDefaultFalse(LogWatchSettingKey.TWO_COUNTRIES_ONE_HOUR_ENABLED));
		settings.setTooManyWrongPasswordsEnabled(logWatchSettingService.getBooleanWithDefaultFalse(LogWatchSettingKey.TOO_MANY_WRONG_PASSWORDS_ENABLED));
		settings.setTooManyWrongPasswordsLimit(logWatchSettingService.getLongWithDefault(LogWatchSettingKey.TOO_MANY_WRONG_PASSWORDS_LIMIT, 20));
		settings.setTooManyTimeLockedAccountsEnabled(logWatchSettingService.getBooleanWithDefaultFalse(LogWatchSettingKey.TOO_MANY_TIME_LOCKED_ACCOUNTS_ENABLED));
		settings.setTooManyTimeLockedAccountsLimit(logWatchSettingService.getLongWithDefault(LogWatchSettingKey.TOO_MANY_TIME_LOCKED_ACCOUNTS_LIMIT, 10));
		settings.setTooManyAccountsLockedByAdminTodayEnabled(logWatchSettingService.getBooleanWithDefaultFalse(LogWatchSettingKey.TOO_MANY_ACCOUNTS_LOCKED_BY_ADMIN_TODAY_ENABLED));
		settings.setTooManyAccountsLockedByAdminTodayLimit(logWatchSettingService.getLongWithDefault(LogWatchSettingKey.TOO_MANY_ACCOUNTS_LOCKED_BY_ADMIN_TODAY_LIMIT, 10));
		settings.setPersonDeadOrIncapacitatedEnabled(logWatchSettingService.getBooleanWithDefaultFalse(LogWatchSettingKey.PERSON_DEAD_OR_DISENFRANCHISED_ENABLED));
	
		return settings;
	}
}
