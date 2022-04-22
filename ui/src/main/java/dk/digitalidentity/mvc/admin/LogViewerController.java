package dk.digitalidentity.mvc.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import dk.digitalidentity.common.dao.model.AuditLog;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.LogAction;
import dk.digitalidentity.common.service.AuditLogService;
import dk.digitalidentity.datatables.model.AuditLogView;
import dk.digitalidentity.mvc.admin.dto.AuditLogDTO;
import dk.digitalidentity.security.RequireSupporter;

@RequireSupporter
@Controller
public class LogViewerController {

	@Autowired
	private AuditLogService auditLogService;
	
	@Autowired
	private ResourceBundleMessageSource resourceBundle;
	
	@GetMapping("/admin/logs")
	public String logs(Model model, Locale locale) {
		model.addAttribute("logActions", LogAction.getSorted(resourceBundle, locale));
		return "admin/log-viewer";
	}

	@GetMapping("/admin/logs/{id}")
	public String logDetail(Model model, @PathVariable("id") Long id) {
		AuditLog auditLog = auditLogService.findById(id);
		if (auditLog == null) {
			return "redirect:/admin/logs";
		}

		model.addAttribute("auditlog", new AuditLogDTO(auditLog));

		return "admin/log-detail";
	}
	
	@GetMapping("/admin/relatedlogs/{id}")
	public String relatedlogs(Model model, @PathVariable("id") String id) {
		List<AuditLog> auditLogs = auditLogService.findByCorrelationId(id);
		
		List<AuditLogView> relatedLogs = new ArrayList<>();
		for (AuditLog auditLog : auditLogs) {
			Person person = auditLog.getPerson();

			AuditLogView auditLogView = new AuditLogView();
			auditLogView.setCpr(auditLog.getCpr());
			auditLogView.setId(auditLog.getId());
			auditLogView.setMessage(auditLog.getMessage());
			auditLogView.setPersonName(auditLog.getPersonName());
			auditLogView.setPersonDomain(auditLog.getPersonDomain());
			auditLogView.setTts(auditLog.getTts());
			//samacount name if not null or empty else userId if not null or empty else null
			auditLogView.setUserId(person != null ? (StringUtils.hasLength(person.getSamaccountName()) ? person.getSamaccountName() : (StringUtils.hasLength(person.getUserId()) ? person.getUserId() : null)) : null);
			
			relatedLogs.add(auditLogView);
		}

		model.addAttribute("relatedlogs", relatedLogs);
		
		return "admin/related-log-viewer";
	}
}
