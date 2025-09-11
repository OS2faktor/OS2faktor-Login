package dk.digitalidentity.mvc.admin;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import dk.digitalidentity.security.RequireAdministrator;

@RequireAdministrator
@Controller
public class MfaLoginHistoryController {

	@GetMapping("/admin/mfahistory")
	public String mfaHistory(Model model) {
		return "admin/mfa-history";
	}
}
