package dk.digitalidentity.common.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.LogWatchSettingDao;
import dk.digitalidentity.common.dao.model.logWatchSetting;
import dk.digitalidentity.common.dao.model.enums.LogWatchSettingKey;

@Service
public class LogWatchSettingService {
	
	@Autowired
	private LogWatchSettingDao logWatchSettingDao;
	
	public boolean getBooleanWithDefaultFalse(LogWatchSettingKey key) {
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
