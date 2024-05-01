package dk.digitalidentity.mvc.admin;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import dk.digitalidentity.security.RequireKodeviserAdministrator;

@RequireKodeviserAdministrator
@Controller
public class KodeviserController {

	@GetMapping("/admin/konfiguration/kodeviser")
	public String listHardwareDevices(Model model) {
		return "admin/kodeviser-manage";
	}
}
