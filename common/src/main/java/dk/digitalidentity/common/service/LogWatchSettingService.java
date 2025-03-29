package dk.digitalidentity.common.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.LogWatchSettingDao;
import dk.digitalidentity.common.dao.model.logWatchSetting;
import dk.digitalidentity.common.dao.model.enums.LogWatchSettingKey;

@Service
public class LogWatchSettingService {
	
	@Autowired
	private LogWatchSettingDao logWatchSettingDao;
	
	@Autowired
	private CommonConfiguration commonConfiguration;
	
	public String getAlarmEmailRecipients(boolean includeHostProvider) {
		StringBuilder builder = new StringBuilder();
		
		String customerEmail = getString(LogWatchSettingKey.ALARM_EMAIL);
		if (StringUtils.hasLength(customerEmail)) {
			builder.append(customerEmail);
		}

		if (includeHostProvider && commonConfiguration.getFullServiceIdP().isEnabled()) {
			if (builder.length() > 0) {
				builder.append(";");
			}
			
			builder.append("os2faktor-overvaagning@digital-identity.dk");
		}

		return builder.toString();
	}

	private Boolean getFullServiceSettingBoolean(LogWatchSettingKey key) {
		switch (key) {
			// enabled in full-service-idp-mode
			case PERSON_DEAD_OR_DISENFRANCHISED_ENABLED:
			case TOO_MANY_TIME_LOCKED_ACCOUNTS_ENABLED:
			case LOG_WATCH_ENABLED:
			case TOO_MANY_WRONG_PASSWORDS_ENABLED:
			case TWO_COUNTRIES_ONE_HOUR_ENABLED:
				return true;

			// disabled in full-service-idp-mode
			case TOO_MANY_WRONG_PASSWORDS_WHITELIST_ENABLED:
				return false;

			// wrong type
			case ALARM_EMAIL:
			case TOO_MANY_WRONG_PASSWORDS_WHITELIST:
			case TOO_MANY_WRONG_PASSWORDS_WHITELIST_LIMIT:
			case TOO_MANY_TIME_LOCKED_ACCOUNTS_LIMIT:
			case TOO_MANY_WRONG_PASSWORDS_LIMIT:
				break;

			// allowed configured by customer
			case TWO_COUNTRIES_ONE_HOUR_GERMANY_ENABLED:
			case TWO_COUNTRIES_ONE_HOUR_SWEEDEN_ENABLED:
				break;
		}

		return null;
	}
	
	private Long getFullServiceSettingLong(LogWatchSettingKey key) {
		switch (key) {
			case TOO_MANY_WRONG_PASSWORDS_WHITELIST_LIMIT:
				return 0L;
			case TOO_MANY_WRONG_PASSWORDS_LIMIT:
				return 1000L;
			case TOO_MANY_TIME_LOCKED_ACCOUNTS_LIMIT:
				return 100L;

			// wrong type
			case ALARM_EMAIL:
			case TOO_MANY_WRONG_PASSWORDS_WHITELIST:
			case PERSON_DEAD_OR_DISENFRANCHISED_ENABLED:
			case TOO_MANY_TIME_LOCKED_ACCOUNTS_ENABLED:
			case LOG_WATCH_ENABLED:
			case TOO_MANY_WRONG_PASSWORDS_ENABLED:
			case TWO_COUNTRIES_ONE_HOUR_ENABLED:
			case TOO_MANY_WRONG_PASSWORDS_WHITELIST_ENABLED:
				break;
	
			// allowed configured by customer
			case TWO_COUNTRIES_ONE_HOUR_GERMANY_ENABLED:
			case TWO_COUNTRIES_ONE_HOUR_SWEEDEN_ENABLED:
				break;
		}

		return null;
	}
	
	public boolean getBooleanWithDefaultFalse(LogWatchSettingKey key) {
		if (commonConfiguration.getFullServiceIdP().isEnabled()) {
			Boolean res = getFullServiceSettingBoolean(key);

			// returns NULL if end-user configuration is allowed in full-service-idp mode
			if (res != null) {
				return res;
			}
		}

		logWatchSetting setting = logWatchSettingDao.getByKey(key);
		if (setting != null) {
			return Boolean.parseBoolean(setting.getValue());
		}

		return false;
	}
	
	public void setBooleanValue(LogWatchSettingKey key, boolean enabled) {
		logWatchSetting setting = logWatchSettingDao.getByKey(key);
		if (setting == null) {
			setting = new logWatchSetting();
			setting.setKey(key);
		}
		
		setting.setValue("" + enabled);
		logWatchSettingDao.save(setting);
	}
	
	public long getLongWithDefault(LogWatchSettingKey key, long defaultValue) {
		if (commonConfiguration.getFullServiceIdP().isEnabled()) {
			Long res = getFullServiceSettingLong(key);
			
			// returns NULL if end-user configuration is allowed in full-service-idp mode
			if (res != null) {
				return res;
			}
		}

		logWatchSetting setting = logWatchSettingDao.getByKey(key);
		if (setting != null) {
			return Long.parseLong(setting.getValue());
		}

		return defaultValue;
	}
	
	public void setLongValue(LogWatchSettingKey key, long value) {
		logWatchSetting setting = logWatchSettingDao.getByKey(key);
		if (setting == null) {
			setting = new logWatchSetting();
			setting.setKey(key);
		}
		
		setting.setValue("" + value);
		logWatchSettingDao.save(setting);
	}
	
	public String getString(LogWatchSettingKey key) {
		logWatchSetting setting = logWatchSettingDao.getByKey(key);
		if (setting != null) {
			return setting.getValue();
		}

		return null;
	}
	
	public void setStringValue(LogWatchSettingKey key, String value) {
		logWatchSetting setting = logWatchSettingDao.getByKey(key);
		if (setting == null) {
			setting = new logWatchSetting();
			setting.setKey(key);
		}
		
		setting.setValue(value);
		logWatchSettingDao.save(setting);
	}
}
