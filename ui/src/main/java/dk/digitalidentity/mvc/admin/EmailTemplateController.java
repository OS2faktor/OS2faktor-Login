package dk.digitalidentity.mvc.admin;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import dk.digitalidentity.common.dao.model.EmailTemplate;
import dk.digitalidentity.common.service.EmailTemplateService;
import dk.digitalidentity.mvc.admin.dto.EmailTemplateDTO;
import dk.digitalidentity.security.RequireAdministrator;

@RequireAdministrator
@Controller
public class EmailTemplateController {

	@Autowired
	private EmailTemplateService emailTemplateService;
	
	@GetMapping("/admin/emailtemplates")
	public String editTemplate(Model model) {
		List<EmailTemplate> templates = emailTemplateService.findAll();
		List<EmailTemplateDTO> templateDTOs = templates.stream()
				.map(t -> EmailTemplateDTO.builder()
						.id(t.getId())
						.message(t.getMessage())
						.title(t.getTitle())
						.templateTypeText(t.getTemplateType().getMessage())
						.enabled(t.isEnabled())
						.emailAllowed(t.getTemplateType().isEmail())
						.eboksAllowed(t.getTemplateType().isEboks())
						.emailEnabled(t.isEmail())
						.eboksEnabled(t.isEboks())
						.build())
				.collect(Collectors.toList());
		
		model.addAttribute("templates", templateDTOs);

		return "admin/email-templates";
	}
}
