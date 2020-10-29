package dk.digitalidentity.mvc.admin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import dk.digitalidentity.common.dao.model.LoginInfoMessage;
import dk.digitalidentity.common.service.LoginInfoMessageService;
import dk.digitalidentity.mvc.admin.dto.LoginInfoMessageDTO;
import dk.digitalidentity.security.RequireSupporter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequireSupporter
public class InfoboxController {

	@Autowired
	private LoginInfoMessageService loginInfoMessageService;

	@GetMapping("/admin/infoboks")
	public String getInfoboxConfiguration(Model model) {
		LoginInfoMessage infobox = loginInfoMessageService.getInfobox();
		if (infobox == null) {
			log.error("Failed to extract infobox from database!");
			return "redirect:/admin";
		}

		LoginInfoMessageDTO infoboxDTO = new LoginInfoMessageDTO();
		infoboxDTO.setContent(infobox.getMessage());
		infoboxDTO.setEnabled(infobox.isEnabled());

		model.addAttribute("infobox", infoboxDTO);

		return "admin/configure-infobox";
	}

	@PostMapping("/admin/infoboks")
	public String saveInfoboxConfiguration(Model model, LoginInfoMessageDTO infoboxDTO, RedirectAttributes redirectAttributes) {
		LoginInfoMessage infobox = loginInfoMessageService.getInfobox();
		if (infobox == null) {
			infobox = new LoginInfoMessage();
		}

		infobox.setMessage(infoboxDTO.getContent());
		infobox.setEnabled(infoboxDTO.isEnabled());
		loginInfoMessageService.save(infobox);

		redirectAttributes.addFlashAttribute("flashMessage", "Infoboks opdateret");

		return "redirect:/admin";
	}

}
