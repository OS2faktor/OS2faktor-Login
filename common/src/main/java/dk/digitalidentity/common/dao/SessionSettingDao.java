package dk.digitalidentity.common.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.SessionSetting;

public interface SessionSettingDao extends JpaRepository<SessionSetting, Long> {
	SessionSetting getById(long id);
	List<SessionSetting> findByDomain(Domain domain);
}
