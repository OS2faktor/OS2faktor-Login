package dk.digitalidentity.mvc.admin;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import dk.digitalidentity.common.dao.model.Group;
import dk.digitalidentity.common.dao.model.Keystore;
import dk.digitalidentity.common.dao.model.PersonAttribute;
import dk.digitalidentity.common.dao.model.SqlServiceProviderConfiguration;
import dk.digitalidentity.common.dao.model.enums.SettingsKey;
import dk.digitalidentity.common.service.GroupService;
import dk.digitalidentity.common.service.KombitSubSystemService;
import dk.digitalidentity.common.service.PersonAttributeService;
import dk.digitalidentity.common.service.SettingService;
import dk.digitalidentity.common.service.SqlServiceProviderConfigurationService;
import dk.digitalidentity.mvc.admin.dto.serviceprovider.ServiceProviderDTO;
import dk.digitalidentity.security.RequireServiceProviderAdmin;
import dk.digitalidentity.service.KeystoreService;
import dk.digitalidentity.service.MetadataService;

@RequireServiceProviderAdmin
@Controller
public class ServiceProviderController {

	@Autowired
	private SqlServiceProviderConfigurationService sqlServiceProviderConfigurationService;

	@Autowired
	private MetadataService metadataService;

	@Autowired
	private KombitSubSystemService kombitSubsystemService;

	@Autowired
	private PersonAttributeService personAttributeSetService;
	
	@Autowired
	private SettingService settingService;
	
	@Autowired
	private KeystoreService keystoreService;
	
	@Autowired
	private GroupService groupService;
	
	@GetMapping("/admin/konfiguration/tjenesteudbydere")
	public String getServiceProviders(Model model) throws Exception {
		ArrayList<ServiceProviderDTO> serviceProviders = new ArrayList<>();
		serviceProviders.addAll(metadataService.getStaticServiceProviderDTOs(false));

		List<SqlServiceProviderConfiguration> sqlSPs = sqlServiceProviderConfigurationService.getAll();
		for (SqlServiceProviderConfiguration sqlSP : sqlSPs) {
			serviceProviders.add(metadataService.getMetadataDTO(sqlSP, false));
		}

		model.addAttribute("serviceproviders", serviceProviders);

		return "admin/serviceproviders-list";
	}
	
	@GetMapping("/admin/konfiguration/tjenesteudbydere/rulehelp")
	public String ruleHelp(Model model) {
		return "admin/advanced-rule-help";
	}
	
	@GetMapping("/admin/konfiguration/tjenesteudbydere/metadata")
	public String getMetadata(Model model) throws Exception {
		LocalDateTime tts = settingService.getLocalDateTimeSetting(SettingsKey.CERTIFICATE_ROLLOVER_TTS);
		boolean plannedRollover = false;
		
		if (tts.isBefore(LocalDateTime.parse(SettingsKey.CERTIFICATE_ROLLOVER_TTS.getDefaultValue()))) {
			plannedRollover = true;
		}
		
		model.addAttribute("planedRollOver", plannedRollover);
		model.addAttribute("planedRollOverTts", tts.toString().replace('T', ' '));
		
		List<Keystore> keystores = keystoreService.findAll();
		for (Keystore keystore : keystores) {
			if (keystore.isPrimaryForIdp()) {
				model.addAttribute("primaryCertName", keystore.getSubjectDn());
			}
			else {
				model.addAttribute("secondaryCertName", keystore.getSubjectDn());
			}
		}
		
		return "admin/serviceproviders-metadata";
	}

	@GetMapping("/admin/konfiguration/tjenesteudbydere/{id}")
	public String getViewServiceProvider(Model model, @PathVariable("id") String id) throws Exception {
		ServiceProviderDTO serviceProviderDTO;
		try {
			SqlServiceProviderConfiguration spConfig = sqlServiceProviderConfigurationService.getById(Long.parseLong(id));
			if (spConfig == null) {
				return "redirect:/admin/konfiguration/tjenesteudbydere";
			}
			
			serviceProviderDTO = metadataService.getMetadataDTO(spConfig, true);
		}
		catch (Exception ex) {
			serviceProviderDTO  = metadataService.getStaticServiceProviderDTOByName(id);
		}

		if (serviceProviderDTO == null) {
			return "redirect:/admin/konfiguration/tjenesteudbydere";
		}

		if (serviceProviderDTO.isKombitServiceProvider()) {
			model.addAttribute("kombitSubsystems", kombitSubsystemService.findAll());
		}
		
		model.addAttribute("serviceprovider", serviceProviderDTO);
		model.addAttribute("attributeValueMap", personAttributeSetService.getAttributeValueMappings(true));

		return "admin/serviceprovider-view";
	}

	@GetMapping("/admin/konfiguration/tjenesteudbydere/{id}/edit")
	public String getEditServiceProvider(Model model, @PathVariable("id") long id) throws Exception {
		SqlServiceProviderConfiguration spConfig = null;
		ServiceProviderDTO serviceProviderDTO = null;

		if (id == 0) {
			serviceProviderDTO = new ServiceProviderDTO();
		}
		else {
			spConfig = sqlServiceProviderConfigurationService.getById(id);
			if (spConfig == null) {
				return "redirect:/admin/konfiguration/tjenesteudbydere";
			}

			serviceProviderDTO = metadataService.getMetadataDTO(spConfig, false);
		}

		model.addAttribute("serviceprovider", serviceProviderDTO);
		model.addAttribute("attributeValueMap", personAttributeSetService.getAttributeValueMappings(true));
		
		List<Group> groups = groupService.getAll();
		model.addAttribute("groups", groups);
		model.addAttribute("showGroups", groups.size() > 0);

		return "admin/serviceprovider-edit";
	}

	@GetMapping("/admin/konfiguration/person/attributes")
	public String getPersonAttributes(Model model) {
		List<PersonAttribute> allPersonAttributes = personAttributeSetService.getAll();

		model.addAttribute("attributes", allPersonAttributes);
		return "admin/configure-person-attributes";
	}
}
