package dk.digitalidentity.common.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.common.dao.model.logWatchSetting;
import dk.digitalidentity.common.dao.model.enums.LogWatchSettingKey;

public interface LogWatchSettingDao extends JpaRepository<logWatchSetting, Long> {
	logWatchSetting getByKey(LogWatchSettingKey key);
}
