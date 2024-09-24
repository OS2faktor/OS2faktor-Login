package dk.digitalidentity.common.service;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.SettingDao;
import dk.digitalidentity.common.dao.model.Setting;
import dk.digitalidentity.common.dao.model.enums.SettingsKey;

@EnableCaching
@Service
public class SettingService {
	
	@Autowired
	private SettingDao settingDao;
	
	@Autowired
	private SettingService self;
	
	@Cacheable("booleanSetting")
	public boolean getBoolean(SettingsKey key) {
		Setting setting = settingDao.getByKey(key);
		if (setting == null) {
			setting = new Setting();
			setting.setKey(key);
			setting.setValue(key.getDefaultValue());

			settingDao.save(setting);
		}

		return Boolean.parseBoolean(setting.getValue());
	}

	public LocalDateTime getLocalDateTimeSetting(SettingsKey key) {
		Setting setting = settingDao.getByKey(key);
		if (setting == null) {
			setting = new Setting();
			setting.setKey(key);
			setting.setValue(key.getDefaultValue());

			settingDao.save(setting);
		}

		return LocalDateTime.parse(setting.getValue());
	}
	
	public void setLocalDateTimeSetting(SettingsKey key, LocalDateTime tts) {
		Setting setting = settingDao.getByKey(key);
		if (setting == null) {
			setting = new Setting();
			setting.setKey(key);
		}

		setting.setValue(tts.toString());
		settingDao.save(setting);
	}

	public void setBoolean(SettingsKey key, boolean value) {
		Setting setting = settingDao.getByKey(key);
		if (setting == null) {
			setting = new Setting();
			setting.setKey(key);
		}

		setting.setValue(Boolean.toString(value));
		settingDao.save(setting);
	}
	
	@Caching(evict = {
		@CacheEvict(value = "booleanSetting", allEntries = true)
	})
	public void cleanupCache() {

	}
	
	@Scheduled(fixedRate = 10 * 60 * 1000)
	public void cleanUpTaskRealtimeValues() {
		self.cleanupCache();
	}
}
