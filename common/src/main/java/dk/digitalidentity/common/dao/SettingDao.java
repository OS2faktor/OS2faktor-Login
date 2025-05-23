package dk.digitalidentity.common.dao;

import dk.digitalidentity.common.dao.model.Setting;
import dk.digitalidentity.common.dao.model.enums.SettingKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingDao extends JpaRepository<Setting, Long> {
	Setting getByKey(SettingKey key);
}
