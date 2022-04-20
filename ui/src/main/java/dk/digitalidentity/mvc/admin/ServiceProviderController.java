package dk.digitalidentity.mvc.admin;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import dk.digitalidentity.common.dao.model.SqlServiceProviderConfiguration;
import dk.digitalidentity.common.service.KombitSubSystemService;
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

		return "admin/serviceprovider-edit";
	}
}
