package dk.digitalidentity.mvc.admin;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import dk.digitalidentity.common.dao.model.RadiusClient;
import dk.digitalidentity.common.service.DomainService;
import dk.digitalidentity.common.service.PersonAttributeService;
import dk.digitalidentity.common.service.RadiusClientService;
import dk.digitalidentity.mvc.admin.dto.RadiusClientDTO;
import dk.digitalidentity.security.RequireServiceProviderAdmin;

@RequireServiceProviderAdmin
@Controller
public class RadiusClientController {
	
	@Autowired
	private RadiusClientService radiusClientService;
	
	@Autowired
	private DomainService domainService;

	@Autowired
	private PersonAttributeService personAttributeService;
	
	@GetMapping("/admin/konfiguration/radiusklienter")
	public String getRadiusClients(Model model) throws Exception {
		model.addAttribute("radiusClients", radiusClientService.getAll());

		return "admin/radius-clients-list";
	}

	@GetMapping("/admin/konfiguration/radiusklienter/{id}/edit")
	public String getEditRadiusClient(Model model, @PathVariable("id") long id) throws Exception {
		RadiusClient radiusClient = null;
		RadiusClientDTO radiusClientDTO = null;

		if (id == 0) {
			radiusClientDTO = new RadiusClientDTO();
		}
		else {
			radiusClient = radiusClientService.getById(id);
			if (radiusClient == null) {
				return "redirect:/admin/konfiguration/radiusklienter";
			}

			radiusClientDTO = new RadiusClientDTO(radiusClient);
			model.addAttribute("edit", true);
		}

		model.addAttribute("radiusClient", radiusClientDTO);
		model.addAttribute("domains", domainService.getAllParents());
		model.addAttribute("attributeValueMap", personAttributeService.getAttributeValueMappings(true));

		return "admin/radius-client-edit";
	}
}
