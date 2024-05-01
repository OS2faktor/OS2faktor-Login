package dk.digitalidentity.mvc.admin;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import dk.digitalidentity.common.dao.model.EmailTemplate;
import dk.digitalidentity.common.dao.model.EmailTemplateChild;
import dk.digitalidentity.common.service.DomainService;
import dk.digitalidentity.common.service.EmailTemplateService;
import dk.digitalidentity.mvc.admin.dto.EmailTemplateChildDTO;
import dk.digitalidentity.mvc.admin.dto.EmailTemplateDTO;
import dk.digitalidentity.security.RequireAdministrator;

@RequireAdministrator
@Controller
public class EmailTemplateController {

	@Autowired
	private EmailTemplateService emailTemplateService;

	@Autowired
	private DomainService domainService;
	
	@GetMapping("/admin/emailtemplates")
	public String editTemplate(Model model) {
		List<EmailTemplate> templates = emailTemplateService.findForPersons();

		List<EmailTemplateDTO> templateDTOs = new ArrayList<>();
		for (EmailTemplate template : templates) {
			EmailTemplateDTO emailTemplateDTO = new EmailTemplateDTO();
			emailTemplateDTO.setId(template.getId());
			emailTemplateDTO.setEmailTemplateType(template.getTemplateType());
			emailTemplateDTO.setTemplateTypeText(template.getTemplateType().getMessage());
			emailTemplateDTO.setEmailAllowed(template.getTemplateType().isEmail());
			emailTemplateDTO.setEboksAllowed(template.getTemplateType().isEboks());

			List<EmailTemplateChildDTO> templateChildDTOs = new ArrayList<>();
			for (EmailTemplateChild child : template.getChildren()) {
				EmailTemplateChildDTO childDto = new EmailTemplateChildDTO();
				childDto.setId(child.getId());
				childDto.setEmailEnabled(child.isEmail());
				childDto.setEboksEnabled(child.isEboks());
				childDto.setEnabled(child.isEnabled());
				childDto.setMessage(child.getMessage());
				childDto.setTitle(child.getTitle());
				childDto.setDomainId(child.getDomain().getId());
				templateChildDTOs.add(childDto);
			}

			emailTemplateDTO.setChildren(templateChildDTOs);
			templateDTOs.add(emailTemplateDTO);
		}
		
		model.addAttribute("templates", templateDTOs);
		model.addAttribute("domains", domainService.getAllEmailTemplateDomains());

		return "admin/email-templates";
	}

	@GetMapping("/admin/emailtemplatesLogwatch")
	public String editTemplateLogWatch(Model model) {
		List<EmailTemplate> templates = emailTemplateService.findForLogWatch();

		List<EmailTemplateDTO> templateDTOs = new ArrayList<>();
		for (EmailTemplate template : templates) {
			EmailTemplateDTO emailTemplateDTO = new EmailTemplateDTO();
			emailTemplateDTO.setId(template.getId());
			emailTemplateDTO.setEmailTemplateType(template.getTemplateType());
			emailTemplateDTO.setTemplateTypeText(template.getTemplateType().getMessage());

			List<EmailTemplateChildDTO> templateChildDTOs = new ArrayList<>();
			for (EmailTemplateChild child : template.getChildren()) {
				EmailTemplateChildDTO childDto = new EmailTemplateChildDTO();

				childDto.setId(child.getId());
				childDto.setMessage(child.getMessage());
				childDto.setTitle(child.getTitle());
				templateChildDTOs.add(childDto);
			}

			emailTemplateDTO.setChildren(templateChildDTOs);
			templateDTOs.add(emailTemplateDTO);
		}

		model.addAttribute("templates", templateDTOs);

		return "admin/email-templates-logwatch";
	}
}
