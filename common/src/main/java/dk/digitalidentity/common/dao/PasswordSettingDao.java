package dk.digitalidentity.common.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.PasswordSetting;

public interface PasswordSettingDao extends JpaRepository<PasswordSetting, Long> {
	PasswordSetting getById(long id);
	List<PasswordSetting> findByDomain(Domain domain);
	List<PasswordSetting> findByChangePasswordOnUsersEnabledTrue();
}
