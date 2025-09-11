package dk.digitalidentity.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.PersonDao;
import dk.digitalidentity.common.dao.model.EmailTemplate;
import dk.digitalidentity.common.dao.model.EmailTemplateChild;
import dk.digitalidentity.common.dao.model.LoginAlarm;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.EmailTemplateType;
import dk.digitalidentity.common.dao.model.enums.LogWatchSettingKey;
import dk.digitalidentity.common.dao.model.enums.LoginAlarmType;
import dk.digitalidentity.common.service.EmailTemplateService;
import dk.digitalidentity.common.service.LogWatchSettingService;
import dk.digitalidentity.common.service.LoginAlarmService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.dto.AuditLogFailedLoginDTO;
import dk.digitalidentity.common.service.dto.AuditLogLocationDto;
import dk.digitalidentity.util.IPUtil;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class LogWatcherService {

	private static final String SELECT_WRONG_PASSWORD_NON_WHITELIST_IP =
			"SELECT COUNT(id) AS antal, ip_address, person_id FROM auditlogs a " +
			" WHERE a.log_action = 'WRONG_PASSWORD' " +
			" AND a.tts > ? " +
			" AND a.tts < ? " +
			" GROUP BY ip_address, person_id";

	private static final String SELECT_WRONG_PASSWORD_LAST_HOUR =
			"SELECT COUNT(id) AS antal FROM auditlogs a " +
			" WHERE a.log_action = 'WRONG_PASSWORD' " +
			" AND a.tts > ? " +
			" AND a.tts < ?;";

	private static final String SELECT_LOGIN_AUDITLOGS_WITH_LOCATION =
			"SELECT person_id, location FROM auditlogs a " +
			" INNER JOIN domains d ON (d.name = a.person_domain AND d.non_nsis = 0) " +
			" WHERE a.log_action = 'LOGIN' " +
			" AND a.tts > ? " +
			" AND a.tts < ? " +
			" AND a.location IS NOT NULL " +
			" AND a.location <> 'UNKNOWN'";

	private static final String SELECT_LOGIN_AUDITLOGS_WITH_LOCATION_NON_DK =
			"SELECT DISTINCT person_id, location FROM auditlogs a " +
			" WHERE a.log_action = 'LOGIN' " +
			" AND a.tts > ? " +
			" AND a.tts < ? " +
			" AND a.location IS NOT NULL " +
			" AND a.location <> 'UNKNOWN' " +
			" AND a.location <> 'Denmark'";

	@Qualifier("defaultTemplate")
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EmailTemplateService emailTemplateService;

	@Autowired
	private EmailTemplateSenderService emailTemplateSenderService;

	@Autowired
	private LogWatchSettingService logWatchSettingService;

	@Autowired
	private PersonService personService;

	@Autowired
	private PersonDao personDao;
	
	@Autowired
	private LoginAlarmService loginAlarmService;
	
	@Autowired
	private CommonConfiguration commonConfiguration;

	public void logWatchTooManyWrongPasswordsFromNonWhitelistIP() {
		long limit = logWatchSettingService.getLongWithDefault(LogWatchSettingKey.TOO_MANY_WRONG_PASSWORDS_WHITELIST_LIMIT, 0);
		if (limit == 0) {
			return;
		}

		LocalDateTime.now().minusHours(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
		List<AuditLogFailedLoginDTO> dtoList = jdbcTemplate.query(SELECT_WRONG_PASSWORD_NON_WHITELIST_IP,
				(rs, rowNum) -> failedLoginMapper(rs),
				LocalDateTime.now().minusHours(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

		if (dtoList != null && dtoList.size() > 0) {
			List<IpAddressMatcher> whiteList = getWhitelist();

			for (AuditLogFailedLoginDTO dto : dtoList) {
				if (dto.getAttempts() > limit && !isWhitelistedIP(whiteList, dto.getIpAddress())) {	
					log.warn("Too many wrong passwords from non-whitelisted IP: " + dto.getIpAddress() + " for person " + dto.getPersonId());

					Person person = personService.getById(dto.getPersonId(), p -> {
						p.getDomain().getName();
					});

					if (person == null) {
						continue;
					}

					EmailTemplate emailTemplate = emailTemplateService.findByTemplateType(EmailTemplateType.TOO_MANY_PASSWORD_WRONG_NON_WHITELIST);
					for (EmailTemplateChild child : emailTemplate.getChildren()) {
						if (child.isEnabled() && child.getDomain().getId() == person.getDomain().getId()) {
							// we already alarmed the person on this IP address within the last month
							if (loginAlarmService.countByPersonAndIpAddress(person, dto.getIpAddress()) > 0) {
								continue;
							}
							
							// store that we informed the user about this, so we don't send it later
							LoginAlarm alarm = new LoginAlarm();
							alarm.setAlarmType(LoginAlarmType.IP_ADDRESS);
							alarm.setIpAddress(dto.getIpAddress());
							alarm.setPerson(person);
							alarm.setTts(LocalDateTime.now());
							loginAlarmService.save(alarm);

							String message = EmailTemplateService.safeReplacePlaceholder(child.getMessage(), EmailTemplateService.IP_PLACEHOLDER, dto.getIpAddress());
							message = EmailTemplateService.safeReplacePlaceholder(message, EmailTemplateService.RECIPIENT_PLACEHOLDER, person.getName());
							message = EmailTemplateService.safeReplacePlaceholder(message, EmailTemplateService.USERID_PLACEHOLDER, person.getSamaccountName());

							emailTemplateSenderService.send(person.getEmail(), person.getCpr(), person, child.getTitle(), message, child, false);
						}
					}
				}
			}
		}
	}

	public void logWatchTooManyLockedOnPassword() {
		long limit = logWatchSettingService.getLongWithDefault(LogWatchSettingKey.TOO_MANY_TIME_LOCKED_ACCOUNTS_LIMIT, 0);
		if (limit == 0) {
			return;
		}

		String emails = logWatchSettingService.getAlarmEmailRecipients(true);
		if (!StringUtils.hasLength(emails)) {
			return;
		}

		long logCount = personDao.countByLockedPasswordTrue();

		if (logCount > limit) {
			log.warn("Too many time locked accounts");

			EmailTemplateChild child = null;
			if (commonConfiguration.getFullServiceIdP().isEnabled()) {
				child = new EmailTemplateChild();
				child.setTitle("(OS2faktor " + commonConfiguration.getEmail().getFromName() + ") Overvågning af logs: for mange tids-spærrede brugerkonti");
				child.setMessage("Der er registreret " + logCount + " tidsspærrede brugerkonti indenfor den sidste time, hvilket er over den opsatte grænseværdi på " + limit);
			}
			else {
				EmailTemplate emailTemplate = emailTemplateService.findByTemplateType(EmailTemplateType.TOO_MANY_LOCKED_ACCOUNTS);
				if (emailTemplate.getChildren() != null && emailTemplate.getChildren().size() > 0) {
					child = emailTemplate.getChildren().get(0);
				}
			}
			
			if (child != null) {
				String message = EmailTemplateService.safeReplacePlaceholder(child.getMessage(), EmailTemplateService.LOG_COUNT, String.valueOf(logCount));
				message = EmailTemplateService.safeReplacePlaceholder(message, EmailTemplateService.LIMIT, String.valueOf(limit));
				emailTemplateSenderService.send(emails, null, null, child.getTitle(), message, child, true);
			}
		}
	}
	
	public void logWatchNotifyPersonOnNewCountryLogin() {
		EmailTemplate emailTemplate = emailTemplateService.findByTemplateType(EmailTemplateType.NEW_LOGIN_FOREIGN_COUNTRY);

		List<EmailTemplateChild> children = emailTemplate.getChildren().stream().filter(c -> c.isEnabled()).collect(Collectors.toList());
		if (children.size() == 0) {
			return;
		}
		
		List<AuditLogLocationDto> dtoList = jdbcTemplate.query(SELECT_LOGIN_AUDITLOGS_WITH_LOCATION_NON_DK,
			(rs, rowNum) -> {
				return new AuditLogLocationDto(rs.getLong("person_id"), rs.getString("location"));
			},
			LocalDateTime.now().minusMinutes(70).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
			LocalDateTime.now().minusMinutes(10).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

		if (dtoList != null && dtoList.size() > 0) {
			for (AuditLogLocationDto dto : dtoList) {
				Person person = personService.getById(dto.getPersonId(), p -> {
					p.getDomain().getName();
				});

				if (person == null) {
					continue;
				}

				for (EmailTemplateChild child : children) {
					if (child.getDomain().getId() == person.getDomain().getId()) {
						// we already alarmed the person on this IP address within the last month
						if (loginAlarmService.countByPersonAndCountry(person, dto.getLocation()) > 0) {
							continue;
						}

						// store that we informed the user about this, so we don't send it later
						LoginAlarm alarm = new LoginAlarm();
						alarm.setAlarmType(LoginAlarmType.COUNTRY);
						alarm.setCountry(dto.getLocation());
						alarm.setPerson(person);
						alarm.setTts(LocalDateTime.now());
						loginAlarmService.save(alarm);

						String message = EmailTemplateService.safeReplacePlaceholder(child.getMessage(), EmailTemplateService.COUNTRY, dto.getLocation());
						message = EmailTemplateService.safeReplacePlaceholder(message, EmailTemplateService.RECIPIENT_PLACEHOLDER, person.getName());
						message = EmailTemplateService.safeReplacePlaceholder(message, EmailTemplateService.USERID_PLACEHOLDER, person.getSamaccountName());

						emailTemplateSenderService.send(person.getEmail(), person.getCpr(), person, child.getTitle(), message, child, false);
					}
				}
			}
		}
	}

	public void logWatchTwoCountriesOneHour() {
		boolean translateGermany = logWatchSettingService.getBooleanWithDefaultFalse(LogWatchSettingKey.TWO_COUNTRIES_ONE_HOUR_GERMANY_ENABLED);
		boolean translateSweeden = logWatchSettingService.getBooleanWithDefaultFalse(LogWatchSettingKey.TWO_COUNTRIES_ONE_HOUR_SWEEDEN_ENABLED);
		boolean translateHolland = logWatchSettingService.getBooleanWithDefaultFalse(LogWatchSettingKey.TWO_COUNTRIES_ONE_HOUR_HOLLAND_ENABLED);

		String emails = logWatchSettingService.getAlarmEmailRecipients(false);
		if (!StringUtils.hasLength(emails)) {
			return;
		}

		List<AuditLogLocationDto> logsWithLocation = jdbcTemplate.query(SELECT_LOGIN_AUDITLOGS_WITH_LOCATION,
			(rs, rowNum) -> {
				String location = rs.getString("location");
				if ((translateGermany && "Germany".equals(location)) || (translateSweeden && "Sweden".equals(location)) || (translateHolland && "The Netherlands".equals(location))) {
					location = "Denmark";
				}

				return new AuditLogLocationDto(rs.getLong("person_id"), location);
			},
			LocalDateTime.now().minusMinutes(70).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
			LocalDateTime.now().minusMinutes(10).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

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
			StringBuilder builder = new StringBuilder();

			builder.append("<ul>");
			for (Long key : personIdsWithMultipleLocations.keySet()) {
				Person person = personService.getById(key);

				if (person != null) {
					builder.append("<li>" + person.getName() + " (" + PersonService.getUsername(person) + ") loggede ind fra " + personIdsWithMultipleLocations.get(key) + "</li>");
				}
			}
			builder.append("</ul>");

			String message = builder.toString();

			log.warn("En eller flere personer har logget ind fra forskellige lande indenfor den sidste time: " + message);

			EmailTemplateChild child = null;
			if (commonConfiguration.getFullServiceIdP().isEnabled()) {
				child = new EmailTemplateChild();
				child.setTitle("(OS2faktor " + commonConfiguration.getEmail().getFromName() + ") Overvågning af logs: bruger har logget ind fra flere lande indenfor samme time");
				child.setMessage(message);
			}
			else {
				EmailTemplate emailTemplate = emailTemplateService.findByTemplateType(EmailTemplateType.TWO_COUNTRIES_ONE_HOUR);
				if (emailTemplate.getChildren() != null && emailTemplate.getChildren().size() > 0) {
					child = emailTemplate.getChildren().get(0);
				}
			}
			
			if (child != null) {
				message = EmailTemplateService.safeReplacePlaceholder(child.getMessage(), EmailTemplateService.LIST_OF_PERSONS, message);
				emailTemplateSenderService.send(emails, null, null, child.getTitle(), message, child, true);
			}
		}
	}

	public void logWatchTooManyWrongPassword() {
		long limit = logWatchSettingService.getLongWithDefault(LogWatchSettingKey.TOO_MANY_WRONG_PASSWORDS_LIMIT, 0);
		if (limit == 0) {
			return;
		}

		String emails = logWatchSettingService.getAlarmEmailRecipients(true);
		if (!StringUtils.hasLength(emails)) {
			return;
		}

		long logCount = 0;
		List<Long> logCountList = jdbcTemplate.query(SELECT_WRONG_PASSWORD_LAST_HOUR,
				(rs, rowNum) -> rs.getLong("antal"),
				LocalDateTime.now().minusHours(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

		if (!logCountList.isEmpty()) {
			logCount = logCountList.get(0);
		}

		if (logCount > limit) {
			log.warn("Too many wrong passwords the last hour");
			
			EmailTemplateChild child = null;
			if (commonConfiguration.getFullServiceIdP().isEnabled()) {
				child = new EmailTemplateChild();
				child.setTitle("(OS2faktor " + commonConfiguration.getEmail().getFromName() + ") Overvågning af logs: for mange forkerte kodeord");
				child.setMessage("Der er registreret " + logCount + " forkerte kodeord indenfor den sidste time, hvilket er over den opsatte grænseværdi på " + limit);
			}
			else {
				EmailTemplate emailTemplate = emailTemplateService.findByTemplateType(EmailTemplateType.TOO_MANY_WRONG_PASSWORD);
				if (emailTemplate.getChildren() != null && emailTemplate.getChildren().size() > 0) {
					child = emailTemplate.getChildren().get(0);
				}
			}
			
			if (child != null) {
				String message = EmailTemplateService.safeReplacePlaceholder(child.getMessage(), EmailTemplateService.LOG_COUNT, String.valueOf(logCount));
				emailTemplateSenderService.send(emails, null, null, child.getTitle(), message, child, true);
			}
		}
	}

	private List<IpAddressMatcher> getWhitelist() {
		String whitelistString = logWatchSettingService.getString(LogWatchSettingKey.TOO_MANY_WRONG_PASSWORDS_WHITELIST);
		return IPUtil.createAllowList(whitelistString);
	}

	private boolean isWhitelistedIP(List<IpAddressMatcher> whitelist, String userIp) {
		boolean isWhitelisted = false;

		for (IpAddressMatcher whitelistIp : whitelist) {
			if (whitelistIp.matches(userIp)) {
				isWhitelisted = true;
			}
		}

		return isWhitelisted;
	}
	

	private AuditLogFailedLoginDTO failedLoginMapper(ResultSet rs) throws SQLException {
		AuditLogFailedLoginDTO dto = new AuditLogFailedLoginDTO();
		if (Objects.isNull(rs)) {
			return dto;
		}

		dto.setAttempts(rs.getLong("antal"));
		dto.setPersonId(rs.getLong("person_id"));
		dto.setIpAddress(rs.getString("ip_address"));

		return dto;
	}
}
