package dk.digitalidentity.common.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import dk.digitalidentity.common.dao.AuditLogDao;
import dk.digitalidentity.common.dao.model.AuditLog;
import dk.digitalidentity.common.dao.model.enums.LogAction;
import dk.digitalidentity.common.dao.model.enums.LogAction.ReportType;
import dk.digitalidentity.common.service.dto.AuditLogDTO;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AuditLogService {

	@Autowired
	private AuditLogDao auditLogDao;
	
	@Qualifier("defaultTemplate")
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Qualifier("namedTemplate")
	@Autowired
	private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
	
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
			for (ReportType type : logAction.getReportTypes()) {
				if (type.equals(reportType)) {
					actions.add(logAction);
				}
			}
		}

		if (reportType.equals(ReportType.LOGIN_HISTORY)) {
			return auditLogDao.findByTtsAfterAndLogActionIn(LocalDateTime.now().minusMonths(3), actions.toArray(new LogAction[0]));
		}
		
		return auditLogDao.findByLogActionIn(actions.toArray(new LogAction[0]));
	}
	
	public List<AuditLog> findByReportTypeAndTtsBetween(ReportType reportType, LocalDateTime from, LocalDateTime to, Pageable pageable) {
		List<LogAction> actions = new ArrayList<>();
		
		for (LogAction logAction : LogAction.values()) {
			for (ReportType type : logAction.getReportTypes()) {
				if (type.equals(reportType)) {
					actions.add(logAction);
				}
			}
		}
		
		return auditLogDao.findByTtsBetweenAndLogActionIn(pageable, from, to, actions.toArray(new LogAction[0]));
	}

	@Transactional // This is OK, need to read them for UPDATE operations
	public List<AuditLog> get500WhereLocationNull() {
		return auditLogDao.findFirst500ByLocationNull();
	}
	
	@Transactional // This is OK, have to have a transaction to save detached entities
	public void saveAll(List<AuditLog> logs) {
		auditLogDao.saveAll(logs);
	}
	
	public int count() {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM auditlogs", Integer.class);
	}

	public int countAuditLogsByMonth(LocalDateTime from, LocalDateTime to) {
		MapSqlParameterSource parameters = new MapSqlParameterSource();
		parameters.addValue("from", Timestamp.valueOf(from), Types.TIMESTAMP);
		parameters.addValue("to", Timestamp.valueOf(to), Types.TIMESTAMP);
		
		return namedParameterJdbcTemplate.queryForObject(
				"SELECT count(*) FROM auditlogs a WHERE (a.tts BETWEEN :from AND :to)",
				parameters, Integer.class);
	}

	public int countAuditLogsByMonth(LocalDateTime from, LocalDateTime to, ReportType type) {
		List<String> list = new ArrayList<>();
		for (LogAction logAction : LogAction.values()) {
			for (ReportType reportType : logAction.getReportTypes()) {
				if (Objects.equals(reportType, type)) {
					list.add(logAction.toString());
					break;
				}
			}
		}

		MapSqlParameterSource parameters = new MapSqlParameterSource();
		parameters.addValue("from", Timestamp.valueOf(from), Types.TIMESTAMP);
		parameters.addValue("to", Timestamp.valueOf(to), Types.TIMESTAMP);
		parameters.addValue("type", list);
		
		return namedParameterJdbcTemplate.queryForObject(
				"SELECT count(*) FROM auditlogs a WHERE (a.tts BETWEEN :from AND :to) AND (a.log_action IN (:type))",
				parameters, Integer.class);
	}

	public List<AuditLogDTO> findAllJDBC(Pageable page, LocalDateTime from, LocalDateTime to, ReportType type) {
		List<String> list = new ArrayList<>();
		for (LogAction logAction : LogAction.values()) {
			for (ReportType reportType : logAction.getReportTypes()) {
				if (Objects.equals(reportType, type)) {
					list.add(logAction.toString());
					break;
				}
			}
		}

		MapSqlParameterSource parameters = new MapSqlParameterSource();
		parameters.addValue("from", Timestamp.valueOf(from), Types.TIMESTAMP);
		parameters.addValue("to", Timestamp.valueOf(to), Types.TIMESTAMP);
		parameters.addValue("type", list);
		parameters.addValue("limit", page.getPageSize());
		parameters.addValue("offset", page.getOffset());
				
		List<AuditLogDTO> auditLogs = namedParameterJdbcTemplate.query(
				"SELECT a.correlation_id, a.cpr, a.ip_address, a.log_action, a.message, a.performer_name, a.person_name, a.tts, p.samaccount_name FROM auditlogs a LEFT JOIN persons p on p.id = a.person_id WHERE (a.tts BETWEEN :from AND :to) AND (a.log_action IN (:type)) LIMIT :limit OFFSET :offset",
				parameters, (rs, rowNum) -> mapAuditLogResult(rs));
		
		return auditLogs;
	}
	
	public List<AuditLogDTO> findAllJDBC(Pageable page, LocalDateTime from, LocalDateTime to) {
		MapSqlParameterSource parameters = new MapSqlParameterSource();
		parameters.addValue("from", Timestamp.valueOf(from), Types.TIMESTAMP);
		parameters.addValue("to", Timestamp.valueOf(to), Types.TIMESTAMP);
		parameters.addValue("limit", page.getPageSize());
		parameters.addValue("offset", page.getOffset());
				
		List<AuditLogDTO> auditLogs = namedParameterJdbcTemplate.query(
				"SELECT a.correlation_id, a.cpr, a.ip_address, a.log_action, a.message, a.performer_name, a.person_name, a.tts, p.samaccount_name FROM auditlogs a LEFT JOIN persons p on p.id = a.person_id WHERE (a.tts BETWEEN :from AND :to) LIMIT :limit OFFSET :offset",
				parameters, (rs, rowNum) -> mapAuditLogResult(rs));

		return auditLogs;
	}

	public int countAuditLogsByMonth(LocalDateTime from, LocalDateTime to, LogAction logActionFilter, String messageFilter) {
		MapSqlParameterSource parameters = new MapSqlParameterSource();
		parameters.addValue("from", Timestamp.valueOf(from), Types.TIMESTAMP);
		parameters.addValue("to", Timestamp.valueOf(to), Types.TIMESTAMP);

		String whereMessage = "";
		if (StringUtils.hasLength(messageFilter)) {
			parameters.addValue("message", "%" + messageFilter + "%");
			whereMessage  = " AND (a.message LIKE :message) ";
		}

		String whereAuditLog = "";
		if (logActionFilter != null) {
			parameters.addValue("logAction", logActionFilter.toString());
			whereAuditLog  = " AND (a.log_action = :logAction) ";
		}
		
		return namedParameterJdbcTemplate.queryForObject("SELECT count(*) FROM auditlogs a WHERE (a.tts BETWEEN :from AND :to) " + whereAuditLog + whereMessage, parameters, Integer.class);
	}

	public List<AuditLogDTO> findAllJDBC(Pageable page, LocalDateTime from, LocalDateTime to, LogAction logActionFilter, String messageFilter) {
		MapSqlParameterSource parameters = new MapSqlParameterSource();
		parameters.addValue("from", Timestamp.valueOf(from), Types.TIMESTAMP);
		parameters.addValue("to", Timestamp.valueOf(to), Types.TIMESTAMP);
		parameters.addValue("limit", page.getPageSize());
		parameters.addValue("offset", page.getOffset());

		String whereMessage = "";
		if (StringUtils.hasLength(messageFilter)) {
			parameters.addValue("message", "%" + messageFilter + "%");
			whereMessage  = " AND (a.message LIKE :message) ";
		}

		String whereAuditLog = "";
		if (logActionFilter != null) {
			parameters.addValue("logAction", logActionFilter.toString());
			whereAuditLog  = " AND (a.log_action = :logAction) ";
		}

		String sql = "SELECT a.correlation_id, a.cpr, a.ip_address, a.log_action, a.message, a.performer_name, a.person_name, a.tts, p.samaccount_name FROM auditlogs a LEFT JOIN persons p on p.id = a.person_id WHERE (a.tts BETWEEN :from AND :to) " + whereAuditLog + whereMessage + " LIMIT :limit OFFSET :offset";
		List<AuditLogDTO> auditLogs = namedParameterJdbcTemplate.query(sql, parameters, (rs, rowNum) -> mapAuditLogResult(rs));
		
		return auditLogs;
	}

	private AuditLogDTO mapAuditLogResult(ResultSet rs) {
		try {
			return new AuditLogDTO(
					rs.getString("tts"),
					rs.getString("ip_address"),
					rs.getString("correlation_id"),
					LogAction.valueOf(rs.getString("log_action")),
					rs.getString("message"),
					rs.getString("cpr"),
					rs.getString("person_name"),
					rs.getString("performer_name"),
					rs.getString("samaccount_name"));
		}
		catch (SQLException ex) {
			log.warn("Error occured while trying to read AuditLog from DB", ex);

			return new AuditLogDTO();
		}
	}
}
