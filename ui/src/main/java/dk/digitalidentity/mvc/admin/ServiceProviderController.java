package dk.digitalidentity.mvc.admin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import dk.digitalidentity.common.dao.model.PersonAttribute;
import dk.digitalidentity.common.dao.model.SqlServiceProviderConfiguration;
import dk.digitalidentity.common.service.KombitSubSystemService;
import dk.digitalidentity.common.service.PersonAttributeService;
import dk.digitalidentity.common.service.SqlServiceProviderConfigurationService;
import dk.digitalidentity.mvc.admin.dto.serviceprovider.ServiceProviderDTO;
import dk.digitalidentity.security.RequireServiceProviderAdmin;
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

	@GetMapping("/admin/konfiguration/tjenesteudbydere")
	public String getServiceProviders(Model model) throws Exception {
		ArrayList<ServiceProviderDTO> serviceProviders = new ArrayList<>();
		serviceProviders.addAll(metadataService.getStaticServiceProviderDTOs());

		List<SqlServiceProviderConfiguration> sqlSPs = sqlServiceProviderConfigurationService.getAll();
		for (SqlServiceProviderConfiguration sqlSP : sqlSPs) {
			serviceProviders.add(metadataService.getMetadataDTO(sqlSP, false));
		}

		model.addAttribute("serviceproviders", serviceProviders);

		return "admin/serviceproviders-list";
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
		model.addAttribute("attributeValueMap", getAttributeValueMappings());

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
		model.addAttribute("attributeValueMap", getAttributeValueMappings());

		return "admin/serviceprovider-edit";
	}

	@GetMapping("/admin/konfiguration/person/attributes")
	public String getPersonAttributes(Model model) {
		List<PersonAttribute> allPersonAttributes = personAttributeSetService.getAll();

		model.addAttribute("attributes", allPersonAttributes);
		return "admin/configure-person-attributes";
	}

	private Map<String, String> getAttributeValueMappings() {
		Map<String, String> result = new HashMap<>();


		List<PersonAttribute> allPersonAttributes = personAttributeSetService.getAll();
		if (allPersonAttributes != null && !allPersonAttributes.isEmpty()) {
			allPersonAttributes.forEach(personAttribute -> result.put(personAttribute.getName(), personAttribute.getDisplayName()));
		}

		result.put("userId", "Brugernavn");
		result.put("sAMAccountName", "Windows Brugernavn"); // TODO: this is deprecated, but we are keeping it to support existing SPs setup with this value until they are migrated
		result.put("uuid", "UUID");
		result.put("cpr", "Personnummer");
		result.put("name", "Navn");
		result.put("alias", "Kaldenavn");
		result.put("email", "E-mail");
		result.put("firstname", "Fornavn");
		result.put("lastname", "Efternavn");

		return result;
	}
}
