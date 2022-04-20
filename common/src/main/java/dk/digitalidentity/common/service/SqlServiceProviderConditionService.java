package dk.digitalidentity.common.service;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.SqlServiceProviderConditionDao;
import dk.digitalidentity.common.dao.model.SqlServiceProviderCondition;

@Service
public class SqlServiceProviderConditionService {

	@Autowired
	private SqlServiceProviderConditionDao sqlServiceProviderConditionDao;

	public SqlServiceProviderCondition getById(long id) {
		return sqlServiceProviderConditionDao.getById(id);
	}

	public SqlServiceProviderCondition save(SqlServiceProviderCondition condition) {
		return sqlServiceProviderConditionDao.save(condition);
	}

}
