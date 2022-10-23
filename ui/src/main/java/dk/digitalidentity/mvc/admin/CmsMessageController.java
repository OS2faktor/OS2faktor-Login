package dk.digitalidentity.mvc.admin;

import java.time.LocalDateTime;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import dk.digitalidentity.common.dao.model.CmsMessage;
import dk.digitalidentity.common.service.CmsMessageBundle;
import dk.digitalidentity.common.service.CmsMessageService;
import dk.digitalidentity.mvc.admin.dto.CmsMessageDTO;
import dk.digitalidentity.security.RequireAdministrator;

@RequireAdministrator
@Controller
public class CmsMessageController {
	
	@Autowired
	private CmsMessageBundle cmsMessageBundle;
	
	@Autowired
	private CmsMessageService cmsMessageService;
	
	@GetMapping("/admin/cms/list") 
	public String listCms(Model model) {
		model.addAttribute("cmsMessages", cmsMessageBundle.getAll());

		return "admin/cms-list";
	}
	
	@GetMapping("/admin/cms/edit") 
	public String editCms(Model model, @RequestParam("key") String key) {
		CmsMessageDTO dto = new CmsMessageDTO();
		dto.setDescription(cmsMessageBundle.getDescription(key));
		dto.setKey(key);
		dto.setValue(cmsMessageBundle.getText(key, true)); // bypass cache ;)
		model.addAttribute("cmsMessage", dto);

		return "admin/cms-edit";
	}
	
	@PostMapping("/admin/cms/edit")
	public String saveCms(Model model, CmsMessageDTO cmsMessageDTO) {
		if (cmsMessageDTO.getValue().length() > 65536) {
			CmsMessageDTO dto = new CmsMessageDTO();
			dto.setDescription(cmsMessageBundle.getDescription(cmsMessageDTO.getKey()));
			dto.setKey(cmsMessageDTO.getKey());
			dto.setValue(cmsMessageDTO.getValue()); // bypass cache ;)
			model.addAttribute("cmsMessage", dto);
			model.addAttribute("showError", true);

			return "admin/cms-edit";
		}
		
		PolicyFactory policy = new HtmlPolicyBuilder()
				.allowCommonBlockElements()
				.allowCommonInlineFormattingElements()
				.allowElements("a")
				.allowUrlProtocols("https")
				.allowAttributes("href", "target").onElements("a")
				.allowAttributes("class", "style", "id", "name").globally()
				.toFactory();
		String safeHTML = policy.sanitize(cmsMessageDTO.getValue());
		
		CmsMessage cms = cmsMessageService.getByCmsKey(cmsMessageDTO.getKey());
		if (cms == null) {
			cms = new CmsMessage();
			cms.setCmsKey(cmsMessageDTO.getKey());
		}

		cms.setCmsValue(safeHTML);
		cms.setLastUpdated(LocalDateTime.now());
		cmsMessageService.save(cms);

		return "redirect:/admin/cms/list";
	}
	
	@GetMapping("/admin/cms/logo") 
	public String uploadLogo(Model model) {
		CmsMessage logo = cmsMessageService.getByCmsKey("cms.logo");
		model.addAttribute("logo", logo == null ? "" : logo.getCmsValue());

		return "admin/set-logo";
	}
}
