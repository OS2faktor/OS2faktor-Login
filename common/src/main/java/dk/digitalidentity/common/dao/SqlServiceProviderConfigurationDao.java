package dk.digitalidentity.common.dao;

import dk.digitalidentity.common.dao.model.SqlServiceProviderConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SqlServiceProviderConfigurationDao extends JpaRepository<SqlServiceProviderConfiguration, Long> {
	SqlServiceProviderConfiguration findById(long id);
	SqlServiceProviderConfiguration findByEntityId(String entityId);
}
