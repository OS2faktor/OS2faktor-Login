package dk.digitalidentity.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.enums.LogAction.ReportType;
import dk.digitalidentity.common.service.AuditLogService;
import dk.digitalidentity.common.service.PersonService;

@Service
public class ReportService {

	@Autowired
	private AuditLogService auditLogService;

	@Autowired
	private PersonService personService;
	
	@Autowired
	private ResourceBundleMessageSource resourceBundle;

	public Map<String, Object> getAuditLogReportModel() {
		Map<String, Object> model = new HashMap<>();
		model.put("resourceBundle", resourceBundle);
		model.put("auditLogs", auditLogService.findAllFromLastWeek());

		return model;
	}

	public Map<String, Object> getAuditLogReportModelByDomain(String domain) {
		Map<String, Object> model = new HashMap<>();
		model.put("resourceBundle", resourceBundle);
		model.put("auditLogs", auditLogService.findFromLastWeekAndDomain(domain));

		return model;
	}

	public Map<String, Object> getPersonsReportModel() {
		Map<String, Object> model = new HashMap<>();
		model.put("persons", personService.getAll());

		return model;
	}

	public Map<String, Object> getPersonsReportModelByDomain(Domain domain) {
		Map<String, Object> model = new HashMap<>();
		model.put("persons", personService.getByDomain(domain, true));

		return model;
	}

	public Map<String, Object> getAuditorReportLoginHistoryModel() {
		Map<String, Object> model = new HashMap<>();
		model.put("resourceBundle", resourceBundle);
		model.put("auditLogs", auditLogService.findByReportType(ReportType.LOGIN_HISTORY));

		return model;
	}
	
	public Map<String, Object> getAuditorReportGeneralHistoryModel() {
		Map<String, Object> model = new HashMap<>();
		model.put("resourceBundle", resourceBundle);
		model.put("auditLogs", auditLogService.findByReportType(ReportType.GENERAL_HISTORY));

		return model;
	}
}
