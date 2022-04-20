package dk.digitalidentity.mvc.admin;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import dk.digitalidentity.common.dao.model.PasswordChangeQueue;
import dk.digitalidentity.common.service.PasswordChangeQueueService;
import dk.digitalidentity.config.Constants;
import dk.digitalidentity.security.RequireSupporter;
import dk.digitalidentity.security.SecurityUtil;

@RequireSupporter
@Controller
public class PasswordChangeQueueController {

	@Autowired
	private SecurityUtil securityUtil;

	@Autowired
	private PasswordChangeQueueService passwordChangeQueueService;

	@GetMapping("/admin/password_change_queue")
	public String getPasswordChangeQueue(Model model) {
		List<PasswordChangeQueue> passwordChangeQueue = new ArrayList<>();
		
		if (securityUtil.isAdmin()) {
			passwordChangeQueue = passwordChangeQueueService.getAll();
		}
		else if (securityUtil.hasRole(Constants.ROLE_SUPPORTER)) {
			passwordChangeQueue = passwordChangeQueueService.getByDomain(securityUtil.getPerson().getSupporter().getDomain().getName());
		}

		model.addAttribute("passwordChangeQueue", passwordChangeQueue);

		return "admin/password-change-queue";
	}
}
