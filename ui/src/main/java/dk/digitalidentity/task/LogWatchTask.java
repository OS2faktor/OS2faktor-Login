package dk.digitalidentity.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.dao.model.enums.LogWatchSettingKey;
import dk.digitalidentity.common.service.LogWatchSettingService;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.service.LogWatcherService;

@Component
@EnableScheduling
public class LogWatchTask {
	
	@Autowired
	private OS2faktorConfiguration configuration;
	
	@Autowired
	private LogWatchSettingService logWatchSettingService;
	
	@Autowired
	private LogWatcherService logWatcherService;

	// run every 55 minutes, delayed 5 minutes to spread out load
	@Scheduled(fixedDelay = 55 * 60 * 1000, initialDelay = 5 * 60 * 1000)
	public void watchLogTwoCountriesOneHour() {
		if (configuration.getScheduled().isEnabled() && logWatchSettingService.getBooleanWithDefaultFalse(LogWatchSettingKey.LOG_WATCH_ENABLED) && logWatchSettingService.getBooleanWithDefaultFalse(LogWatchSettingKey.TWO_COUNTRIES_ONE_HOUR_ENABLED)) {
			logWatcherService.logWatchTwoCountriesOneHour();
		}
	}
		
	// run every 55 minutes, delayed 10 minutes to spread out load
	@Scheduled(fixedDelay = 55 * 60 * 1000, initialDelay = 10 * 60 * 1000)
	public void watchLogTooManyLockedOnPassword() {
		if (configuration.getScheduled().isEnabled() && logWatchSettingService.getBooleanWithDefaultFalse(LogWatchSettingKey.LOG_WATCH_ENABLED) && logWatchSettingService.getBooleanWithDefaultFalse(LogWatchSettingKey.TOO_MANY_TIME_LOCKED_ACCOUNTS_ENABLED)) {
			logWatcherService.logWatchTooManyLockedOnPassword();
		}
	}
	
	// run every 55 minutes, delayed 15 minutes to spread out load
	@Scheduled(fixedDelay = 55 * 60 * 1000, initialDelay = 15 * 60 * 1000)
	public void watchLogTooManyWrongPasswords() {
		if (configuration.getScheduled().isEnabled() && logWatchSettingService.getBooleanWithDefaultFalse(LogWatchSettingKey.LOG_WATCH_ENABLED) && logWatchSettingService.getBooleanWithDefaultFalse(LogWatchSettingKey.TOO_MANY_WRONG_PASSWORDS_ENABLED)) {
			logWatcherService.logWatchTooManyWrongPassword();
		}
	}

	// run every 55 minutes, delayed 20 minutes to spread out load
	@Scheduled(fixedDelay = 55 * 60 * 1000, initialDelay = 20 * 60 * 1000)
	public void watchLogTooManyWrongPasswordsFromNonWhitelistIP() {
		if (configuration.getScheduled().isEnabled() && logWatchSettingService.getBooleanWithDefaultFalse(LogWatchSettingKey.LOG_WATCH_ENABLED) && logWatchSettingService.getBooleanWithDefaultFalse(LogWatchSettingKey.TOO_MANY_WRONG_PASSWORDS_WHITELIST_ENABLED)) {
			logWatcherService.logWatchTooManyWrongPasswordsFromNonWhitelistIP();
		}
	}
	
	// run every 55 minutes, delayed 25 minutes to spread out load
	@Scheduled(fixedDelay = 55 * 60 * 1000, initialDelay = 25 * 60 * 1000)
	public void watchLogNewCountryLogins() {
		if (configuration.getScheduled().isEnabled()) {
			logWatcherService.logWatchNotifyPersonOnNewCountryLogin();
		}
	}
}
