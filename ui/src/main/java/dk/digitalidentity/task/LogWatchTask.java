package dk.digitalidentity.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.dao.model.enums.LogWatchSettingKey;
import dk.digitalidentity.common.service.AuditLogService;
import dk.digitalidentity.common.service.LogWatchSettingService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.config.OS2faktorConfiguration;

@Component
@EnableScheduling
public class LogWatchTask {
	
	@Autowired
	private OS2faktorConfiguration configuration;
	
	@Autowired
	private LogWatchSettingService logWatchSettingService;
	
	@Autowired
	private AuditLogService auditLogService;
	
	@Autowired
	private PersonService personService;
	
	// run "midnight"
	@Scheduled(cron = "0 #{new java.util.Random().nextInt(30) + 30} 23 * * ?")
	public void watchLogTooManyLockedByAdmin() {
		if (configuration.getScheduled().isEnabled() && logWatchSettingService.getBooleanWithDefaultFalse(LogWatchSettingKey.LOG_WATCH_ENABLED) && logWatchSettingService.getBooleanWithDefaultFalse(LogWatchSettingKey.TOO_MANY_ACCOUNTS_LOCKED_BY_ADMIN_TODAY_ENABLED)) {
			auditLogService.logWatchTooManyLockedByAdmin();
		}
	}

	// run every 30 minutes, delayed 5 minutes to spread out load
	@Scheduled(fixedDelay = 30 * 60 * 1000, initialDelay = 5 * 60 * 1000)
	public void watchLogTwoCountriesOneHour() {
		if (configuration.getScheduled().isEnabled() && logWatchSettingService.getBooleanWithDefaultFalse(LogWatchSettingKey.LOG_WATCH_ENABLED) && logWatchSettingService.getBooleanWithDefaultFalse(LogWatchSettingKey.TWO_COUNTRIES_ONE_HOUR_ENABLED)) {
			auditLogService.logWatchTwoCountriesOneHour();
		}
	}
		
	// run every 30 minutes, delayed 10 minutes to spread out load
	@Scheduled(fixedDelay = 30 * 60 * 1000, initialDelay = 10 * 60 * 1000)
	public void watchLogTooManyLockedOnPassword() {
		if (configuration.getScheduled().isEnabled() && logWatchSettingService.getBooleanWithDefaultFalse(LogWatchSettingKey.LOG_WATCH_ENABLED) && logWatchSettingService.getBooleanWithDefaultFalse(LogWatchSettingKey.TOO_MANY_TIME_LOCKED_ACCOUNTS_ENABLED)) {
			personService.logWatchTooManyLockedOnPassword();
		}
	}
	
	// run every 30 minutes, delayed 15 minutes to spread out load
	@Scheduled(fixedDelay = 30 * 60 * 1000, initialDelay = 15 * 60 * 1000)
	public void watchLogTooManyWrongPasswords() {
		if (configuration.getScheduled().isEnabled() && logWatchSettingService.getBooleanWithDefaultFalse(LogWatchSettingKey.LOG_WATCH_ENABLED) && logWatchSettingService.getBooleanWithDefaultFalse(LogWatchSettingKey.TOO_MANY_WRONG_PASSWORDS_ENABLED)) {
			auditLogService.logWatchTooManyWrongPassword();
		}
	}
	
}
