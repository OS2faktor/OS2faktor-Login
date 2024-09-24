package dk.digitalidentity.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.LogAction.ReportType;
import dk.digitalidentity.common.service.AuditLogService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.PersonStatisticsService;

@Service
public class ReportService {

	@Autowired
	private AuditLogService auditLogService;

	@Autowired
	private PersonService personService;
	
	@Autowired
	private ResourceBundleMessageSource resourceBundle;

	@Autowired
	private AuditLogReportXlsService auditLogReportXlsService;
	
	@Autowired
	private MitidErhvervCacheService mitidErhvervCacheService;

	@Autowired
	private PersonStatisticsService personStatisticsService;

	public Map<String, Object> getAuditLogReportModelWithAdminActions() {
		Map<String, Object> model = new HashMap<>();
		model.put("resourceBundle", resourceBundle);
		model.put("auditLogs", auditLogService.findByReportType(ReportType.ADMIN_ACTION));

		return model;
	}
	
	public Map<String, Object> getAuditLogReportModel() {
		Map<String, Object> model = new HashMap<>();
		model.put("resourceBundle", resourceBundle);
		model.put("auditLogs", auditLogService.findAllFromLastWeek());
		model.put("auditLogReportXlsService", auditLogReportXlsService);

		return model;
	}

	public Map<String, Object> getAuditLogReportModelByDomain(String domain) {
		Map<String, Object> model = new HashMap<>();
		model.put("resourceBundle", resourceBundle);
		model.put("auditLogs", auditLogService.findFromLastWeekAndDomain(domain));
		model.put("auditLogReportXlsService", auditLogReportXlsService);

		return model;
	}

	public Map<String, Object> getPersonsReportModel() {
		Map<String, Object> model = new HashMap<>();
		List<Person> people = personService.getAll();
		model.put("persons", people);
		model.put("mitIDErhvervCache", mitidErhvervCacheService.findAll());
		model.put("auditLogReportXlsService", auditLogReportXlsService);
		model.put("statistics", personStatisticsService.getAll());

		return model;
	}

	public Map<String, Object> getPersonsReportModelByDomain(Domain domain) {
		Map<String, Object> model = new HashMap<>();
		List<Person> people = personService.getByDomain(domain, true);
		model.put("persons", people);
		model.put("mitIDErhvervCache", mitidErhvervCacheService.findAll());
		model.put("auditLogReportXlsService", auditLogReportXlsService);
		model.put("statistics", personStatisticsService.getAll());

		return model;
	}

	public Map<String, Object> getAuditorReportModel(ReportType type, LocalDateTime from, LocalDateTime to) {
		Map<String, Object> model = new HashMap<>();
		model.put("type", type);
		model.put("from", from);
		model.put("to", to);
		model.put("auditLogReportXlsService", auditLogReportXlsService);

		return model;
	}

	public Map<String, Object> getRolesReportModel() {
		Map<String, Object> model = new HashMap<>();
		model.put("persons", personService.getAll());
		model.put("auditLogReportXlsService", auditLogReportXlsService);

		return model;
	}

	public Map<String, Object> getRolesReportModelByDomain(Domain domain) {
		Map<String, Object> model = new HashMap<>();
		model.put("persons", personService.getByDomain(domain, true));
		model.put("auditLogReportXlsService", auditLogReportXlsService);

		return model;
	}
}
