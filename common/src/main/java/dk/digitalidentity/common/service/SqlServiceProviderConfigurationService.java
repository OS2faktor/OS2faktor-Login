package dk.digitalidentity.common.service;

import dk.digitalidentity.common.dao.SqlServiceProviderConfigurationDao;
import dk.digitalidentity.common.dao.model.SqlServiceProviderConfiguration;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SqlServiceProviderConfigurationService {

	@Autowired
	private SqlServiceProviderConfigurationDao sqlServiceProviderDao;

	public List<SqlServiceProviderConfiguration> getAll() {
		return sqlServiceProviderDao.findAll();
	}

	@Transactional
	public List<SqlServiceProviderConfiguration> getAllLoadedFully() {
		List<SqlServiceProviderConfiguration> all = sqlServiceProviderDao.findAll();
		all.forEach(SqlServiceProviderConfiguration::loadFully);
		return all;
	}

	public SqlServiceProviderConfiguration getById(long id) {
		return sqlServiceProviderDao.findById(id);
	}

	public SqlServiceProviderConfiguration getByEntityId(String entityId) {
		return sqlServiceProviderDao.findByEntityId(entityId);
	}

	public SqlServiceProviderConfiguration save(SqlServiceProviderConfiguration configuration) {
		configuration.setLastUpdated(LocalDateTime.now());

		return sqlServiceProviderDao.save(configuration);
	}

	public void delete(SqlServiceProviderConfiguration configuration) {
		sqlServiceProviderDao.delete(configuration);		
	}
}
