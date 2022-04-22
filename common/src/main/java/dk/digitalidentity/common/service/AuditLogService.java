package dk.digitalidentity.common.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.AuditLogDao;
import dk.digitalidentity.common.dao.model.AuditLog;
import dk.digitalidentity.common.dao.model.enums.LogAction;
import dk.digitalidentity.common.dao.model.enums.LogAction.ReportType;

@Service
public class AuditLogService {

	@Autowired
	private AuditLogDao auditLogDao;

	public AuditLog findById(Long id) {
		return auditLogDao.getById(id);
	}
	
	public List<AuditLog> findByCorrelationId(String id) {
		return auditLogDao.findByCorrelationId(id);
	}
	
	public List<AuditLog> findAllFromLastWeek() {
		return auditLogDao.findAllByTtsAfter(LocalDateTime.now().minus(7, ChronoUnit.DAYS));
	}

	public List<AuditLog> findFromLastWeekAndDomain(String domain) {
		return auditLogDao.findByPersonDomainAndTtsAfter(domain, LocalDateTime.now().minus(7, ChronoUnit.DAYS));
	}

	public List<AuditLog> findByReportType(ReportType reportType) {
		List<LogAction> actions = new ArrayList<>();
		
		for (LogAction logAction : LogAction.values()) {
			if (logAction.getReportType().equals(reportType)) {
				actions.add(logAction);
			}
		}

		if (reportType.equals(ReportType.LOGIN_HISTORY)) {
			return auditLogDao.findByTtsAfterAndLogActionIn(LocalDateTime.now().minusMonths(3), actions.toArray(new LogAction[0]));
		}
		
		return auditLogDao.findByLogActionIn(actions.toArray(new LogAction[0]));
	}
}
