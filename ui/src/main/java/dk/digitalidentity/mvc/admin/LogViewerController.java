package dk.digitalidentity.mvc.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.persistence.EntityNotFoundException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import dk.digitalidentity.common.dao.model.AuditLog;
import dk.digitalidentity.common.dao.model.AuditLogSearchCriteria;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.LogAction;
import dk.digitalidentity.common.service.AuditLogService;
import dk.digitalidentity.datatables.model.AuditLogView;
import dk.digitalidentity.mvc.admin.dto.AuditLogDTO;
import dk.digitalidentity.security.RequireSupporter;
import dk.digitalidentity.service.AuditLogSearchCriteriaService;
import dk.digitalidentity.service.GeoLocateService;
import dk.digitalidentity.service.geo.dto.GeoIP;

@RequireSupporter
@Controller
public class LogViewerController {

	@Autowired
	private AuditLogService auditLogService;
	
	@Autowired
	private ResourceBundleMessageSource resourceBundle;

	@Autowired
	private GeoLocateService geoLocateService;

	@Autowired
	private AuditLogSearchCriteriaService auditLogSearchCriteriaService;

	@GetMapping("/admin/logs")
	public String logs(Model model, Locale locale) {
		List<AuditLogSearchCriteria> searchCriteria = auditLogSearchCriteriaService.findAll();
		model.addAttribute("logActions", LogAction.getSorted(resourceBundle, locale));
		model.addAttribute("searchCriteria", searchCriteria);
		model.addAttribute("emptySearchCriteria", searchCriteria.isEmpty());

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
	
	@GetMapping("/admin/logs/ipLookup/{ip}")
	public String logIPFragment(Model model, @PathVariable("ip") String ip) {
		GeoIP geoIP = geoLocateService.lookupIp(ip);
		if (geoIP != null) {
			model.addAttribute("geoip", geoIP);
		}

		return "admin/logs/fragments/ip-fragment :: ipDetails";
	}
	
	@GetMapping("/admin/relatedlogs/{id}")
	public String relatedlogs(Model model, @PathVariable("id") String id) {
		List<AuditLog> auditLogs = auditLogService.findByCorrelationId(id);
		
		List<AuditLogView> relatedLogs = new ArrayList<>();
		for (AuditLog auditLog : auditLogs) {
			Person person = null;
			try {
				person = auditLog.getPerson();

				if (person != null) {
					person.getSchoolRoles().size(); // trigger Hibernate exception if person does not exist in DB
				}
			}
			catch (EntityNotFoundException ex) {
				person = null; // if the physical person row was deleted, this will throw an exception because of Hibernate Lazy loading will not allow null values
			}

			AuditLogView auditLogView = new AuditLogView();
			auditLogView.setCpr(auditLog.getCpr());
			auditLogView.setId(auditLog.getId());
			auditLogView.setMessage(auditLog.getMessage());
			auditLogView.setPersonName(auditLog.getPersonName());
			auditLogView.setPersonDomain(auditLog.getPersonDomain());
			auditLogView.setTts(auditLog.getTts());
			auditLogView.setUserId(person != null ? person.getSamaccountName() : null);
			
			relatedLogs.add(auditLogView);
		}

		model.addAttribute("relatedlogs", relatedLogs);
		
		return "admin/related-log-viewer";
	}
}
