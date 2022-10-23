package dk.digitalidentity.common.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.common.dao.AuditLogDao;
import dk.digitalidentity.common.dao.model.AuditLog;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.LogAction;
import dk.digitalidentity.common.dao.model.enums.LogAction.ReportType;
import dk.digitalidentity.common.dao.model.enums.LogWatchSettingKey;
import dk.digitalidentity.common.service.dto.AuditLogLocationDto;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AuditLogService {

	// allow for 10 minutes lagtime, to ensure we get the locations put on the auditlog entries
	private static final String SELECT_LOGIN_AUDITLOGS_WITH_LOCATION =
			"SELECT person_id, location, tts FROM auditlogs a " +
			" WHERE a.log_action = \"LOGIN\" " +
			" AND a.tts > DATE_SUB(NOW(), INTERVAL '70' MINUTE) " +
			" AND a.tts < DATE_SUB(NOW(), INTERVAL '10' MINUTE) " + 
			" AND a.location IS NOT NULL " +
			" AND a.location <> \"UNKNOWN\"";

	private static final String SELECT_WRONG_PASSWORD_LAST_HOUR =
			"SELECT COUNT(id) FROM auditlogs a " +
			" WHERE a.log_action = \"WRONG_PASSWORD\" " +
			" AND a.tts > DATE_SUB(NOW(), INTERVAL '1' HOUR);";

	@Autowired
	private AuditLogDao auditLogDao;
	
	@Autowired
	private LogWatchSettingService logWatchSettingService;
	
	@Qualifier("defaultTemplate")
	@Autowired
	private JdbcTemplate jdbcTemplate;
	
	@Autowired
	private EmailService emailService;
	
	@Autowired
	private PersonService personService;
		
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
	
	public List<AuditLog> findByReportTypeAndTtsBetween(ReportType reportType, LocalDateTime from, LocalDateTime to) {
		List<LogAction> actions = new ArrayList<>();
		
		for (LogAction logAction : LogAction.values()) {
			if (logAction.getReportType().equals(reportType)) {
				actions.add(logAction);
			}
		}
		
		return auditLogDao.findByTtsBetweenAndLogActionIn(from, to, actions.toArray(new LogAction[0]));
	}

	public List<AuditLog> get500WhereLocationNull() {
		return auditLogDao.findFirst500ByLocationNull();
	}
	
	public void saveAll(List<AuditLog> logs) {
		auditLogDao.saveAll(logs);
	}
	
	@Transactional
	public void logWatchTwoCountriesOneHour() {
		List<AuditLogLocationDto> logsWithLocation = jdbcTemplate.query(SELECT_LOGIN_AUDITLOGS_WITH_LOCATION,
				(rs, rowNum) -> new AuditLogLocationDto(rs.getLong("person_id"), rs.getString("location")));
		
		Map<Long, String> personIdsWithMultipleLocations = new HashMap<>();
		Map<Long, String> personLocations = new HashMap<>();

		for (AuditLogLocationDto dto : logsWithLocation) {
			if (personIdsWithMultipleLocations.containsKey(dto.getPersonId())) {
				continue;
			}

			if (personLocations.containsKey(dto.getPersonId())) {
				if (!Objects.equals(dto.getLocation(), personLocations.get(dto.getPersonId()))) {
					personIdsWithMultipleLocations.put(dto.getPersonId(), dto.getLocation() + " og " + personLocations.get(dto.getPersonId()));
				}
			}
			else {
				personLocations.put(dto.getPersonId(), dto.getLocation());
			}
	    }
		
		if (!personIdsWithMultipleLocations.isEmpty()) {
			String subject = "Overvågning af logs: to forskellige lande på en time";
			StringBuilder builder = new StringBuilder();
			builder.append("En eller flere personer har logget ind fra forskellige lande indenfor den sidste time.<br/><br/>Personer:<br/><ul>");
			for (Long key : personIdsWithMultipleLocations.keySet()) {
				Person person = personService.getById(key);
				if (person != null) {
					builder.append("<li>" + person.getName() + " (" + PersonService.getUsername(person) + ") loggede ind fra " + personIdsWithMultipleLocations.get(key) + "</li>");
				}
			}
			builder.append("</ul>");

			String message = builder.toString();
			log.warn("En eller flere personer har logget ind fra forskellige lande indenfor den sidste time: " + message);
			
			emailService.sendMessage(logWatchSettingService.getString(LogWatchSettingKey.ALARM_EMAIL), subject, message);
		}
	}
	
	@Transactional
	public void logWatchTooManyLockedByAdmin() {
		long limit = logWatchSettingService.getLongWithDefault(LogWatchSettingKey.TOO_MANY_ACCOUNTS_LOCKED_BY_ADMIN_TODAY_LIMIT, 0);
		if (limit == 0) {
			return;
		}
		
		LocalDate today = LocalDate.now();
		LocalDateTime startOfDay = today.atStartOfDay();
		long logCount = auditLogDao.countByTtsAfterAndLogAction(startOfDay, LogAction.DEACTIVATE_BY_ADMIN);
		if (logCount > limit) {
			log.warn("Too many people locked by admin today");
			
			String subject = "Overvågning af logs: For mange spærret af administrator";
			String message = "Antallet af personer spærret af en administrator i dag har oversteget grænsen på " + limit + " personer.<br/>I dag er " + logCount + " personer blevet spærret af en administrator.";
			emailService.sendMessage(logWatchSettingService.getString(LogWatchSettingKey.ALARM_EMAIL), subject, message);
		}
	}

	@Transactional
	public void logWatchTooManyWrongPassword() {
		long limit = logWatchSettingService.getLongWithDefault(LogWatchSettingKey.TOO_MANY_WRONG_PASSWORDS_LIMIT, 0);
		if (limit == 0) {
			return;
		}
		
		long logCount = 0;
		List<Long> logCountList = jdbcTemplate.query(SELECT_WRONG_PASSWORD_LAST_HOUR,
				(rs, rowNum) -> rs.getLong("COUNT(id)"));
		if (!logCountList.isEmpty()) {
			logCount = logCountList.get(0);
		}
		
		if (logCount > limit) {
			log.warn("Too many wrong passwords the last hour");
			
			String subject = "Overvågning af logs: For mange forkerte kodeord";
			String message = "Antallet af forkerte kodeord den sidste time har oversteget grænsen på " + limit + ".<br/>Den sidste time er der tastet forkert kodeord " + logCount + " gange.";
			emailService.sendMessage(logWatchSettingService.getString(LogWatchSettingKey.ALARM_EMAIL), subject, message);
		}
	}
}
