package dk.digitalidentity.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.service.AuditLogService;
import dk.digitalidentity.common.service.PersonService;

@Service
public class ReportService {

	@Autowired
	private AuditLogService auditLogService;

	@Autowired
	private PersonService personService;

	public Map<String, Object> getAuditLogReportModel() {
		Map<String, Object> model = new HashMap<>();
		model.put("auditLogs", auditLogService.findAllFromLastWeek());

		return model;
	}

	public Map<String, Object> getAuditLogReportModelByDomain(String domain) {
		Map<String, Object> model = new HashMap<>();
		model.put("auditLogs", auditLogService.findFromLastWeekAndDomain(domain));

		return model;
	}

	public Map<String, Object> getPersonsReportModel() {
		Map<String, Object> model = new HashMap<>();
		model.put("persons", personService.getAll());

		return model;
	}

	public Map<String, Object> getPersonsReportModelByDomain(String domain) {
		Map<String, Object> model = new HashMap<>();
		model.put("persons", personService.getByDomain(domain));

		return model;
	}
}
