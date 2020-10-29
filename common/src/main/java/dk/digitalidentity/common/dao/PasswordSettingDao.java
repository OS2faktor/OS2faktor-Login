package dk.digitalidentity.common.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.common.dao.model.PasswordSetting;

public interface PasswordSettingDao extends JpaRepository<PasswordSetting, Long> {
	PasswordSetting getById(long id);
}
