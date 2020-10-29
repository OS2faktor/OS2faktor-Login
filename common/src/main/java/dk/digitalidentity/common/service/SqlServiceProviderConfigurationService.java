package dk.digitalidentity.common.service;

import dk.digitalidentity.common.dao.SqlServiceProviderConfigurationDao;
import dk.digitalidentity.common.dao.model.SqlServiceProviderConfiguration;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SqlServiceProviderConfigurationService {

	@Autowired
	private SqlServiceProviderConfigurationDao sqlServiceProviderDao;

	public List<SqlServiceProviderConfiguration> getAll() { return sqlServiceProviderDao.findAll(); }

	public SqlServiceProviderConfiguration getById(Long id) {
		return sqlServiceProviderDao.getById(id);
	}

	public SqlServiceProviderConfiguration getByEntityId(String entityId) {
		return sqlServiceProviderDao.getByEntityId(entityId);
	}
}
