package dk.digitalidentity.common.service;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.SettingDao;
import dk.digitalidentity.common.dao.model.Setting;
import dk.digitalidentity.common.dao.model.enums.SettingKey;

@EnableCaching
@Service
public class SettingService {
	
	@Autowired
	private SettingDao settingDao;
	
	@Cacheable("booleanSetting")
	public boolean getBoolean(SettingKey key) {
		Setting setting = settingDao.getByKey(key);
		if (setting == null) {
			setting = new Setting();
			setting.setKey(key);
			setting.setValue(key.getDefaultValue());

			settingDao.save(setting);
		}

		return Boolean.parseBoolean(setting.getValue());
	}
	
	@Cacheable("stringSetting")
	public String getString(SettingKey key) {
		Setting setting = settingDao.getByKey(key);
		if (setting == null) {
			setting = new Setting();
			setting.setKey(key);
			setting.setValue(key.getDefaultValue());

			settingDao.save(setting);
		}

		return setting.getValue();
	}

	@Cacheable("dateSetting")
	public LocalDateTime getLocalDateTimeSetting(SettingKey key) {
		Setting setting = settingDao.getByKey(key);
		if (setting == null) {
			setting = new Setting();
			setting.setKey(key);
			setting.setValue(key.getDefaultValue());

			settingDao.save(setting);
		}

		return LocalDateTime.parse(setting.getValue());
	}

	@Cacheable("longSetting")
	public Long getLong(SettingKey key) {
		Setting setting = settingDao.getByKey(key);
		if (setting == null) {
			setting = new Setting();
			setting.setKey(key);
			setting.setValue(key.getDefaultValue());

			settingDao.save(setting);
		}

		return setting.getValue() == null || setting.getValue().isEmpty() ? null : Long.parseLong(setting.getValue());
	}
	
	public void setLocalDateTimeSetting(SettingKey key, LocalDateTime tts) {
		Setting setting = settingDao.getByKey(key);
		if (setting == null) {
			setting = new Setting();
			setting.setKey(key);
		}

		setting.setValue(tts.toString());
		settingDao.save(setting);
	}

	public void setBoolean(SettingKey key, boolean value) {
		Setting setting = settingDao.getByKey(key);
		if (setting == null) {
			setting = new Setting();
			setting.setKey(key);
		}

		setting.setValue(Boolean.toString(value));
		settingDao.save(setting);
	}
	
	public void setString(SettingKey key, String value) {
		Setting setting = settingDao.getByKey(key);
		if (setting == null) {
			setting = new Setting();
			setting.setKey(key);
		}

		setting.setValue(value);
		settingDao.save(setting);
	}

	public void setLong(SettingKey key, Long value) {
		Setting setting = settingDao.getByKey(key);
		if (setting == null) {
			setting = new Setting();
			setting.setKey(key);
		}

		setting.setValue(value == null ? "" : value.toString());
		settingDao.save(setting);
	}
	
	@Caching(evict = {
		@CacheEvict(value = "booleanSetting", allEntries = true),
		@CacheEvict(value = "stringSetting", allEntries = true),
		@CacheEvict(value = "dateSetting", allEntries = true),
		@CacheEvict(value = "longSetting", allEntries = true)
	})
	public void cleanupCache() {

	}
}
