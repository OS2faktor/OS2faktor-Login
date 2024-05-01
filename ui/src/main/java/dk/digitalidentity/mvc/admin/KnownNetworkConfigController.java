package dk.digitalidentity.mvc.admin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import dk.digitalidentity.common.service.KnownNetworkService;
import dk.digitalidentity.security.RequireAdministrator;

@Controller
public class KnownNetworkConfigController {

	@Autowired
	private KnownNetworkService knownNetworkService;

	@RequireAdministrator
	@GetMapping("/admin/konfiguration/knownNetworks")
	public String knownNetworksList(Model model) {
		model.addAttribute("knownNetworks", knownNetworkService.getAll());
		
		return "admin/knownNetworks/view";
	}

	@RequireAdministrator
	@GetMapping("/admin/konfiguration/knownNetworks/edit")
	public String knownNetworksEdit(Model model) {
		model.addAttribute("knownNetworks", knownNetworkService.getAll());
		
		return "admin/knownNetworks/edit";
	}
}
