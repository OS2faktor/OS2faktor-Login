package dk.digitalidentity.common.dao;

import dk.digitalidentity.common.dao.model.SqlServiceProviderCondition;
import dk.digitalidentity.common.dao.model.SqlServiceProviderConfiguration;
import dk.digitalidentity.common.dao.model.enums.SqlServiceProviderConditionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SqlServiceProviderConditionDao extends JpaRepository<SqlServiceProviderCondition, Long> {
	SqlServiceProviderCondition getById(Long id);
	List<SqlServiceProviderCondition> findByConfigurationAndType(SqlServiceProviderConfiguration configuration, SqlServiceProviderConditionType type);
}
