package dk.digitalidentity.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.AuditLogSearchCriteriaDao;
import dk.digitalidentity.common.dao.model.AuditLogSearchCriteria;

@Service
public class AuditLogSearchCriteriaService {

	@Autowired
	private AuditLogSearchCriteriaDao auditLogSearchCriteriaDao;

	public AuditLogSearchCriteria save(AuditLogSearchCriteria entity) {
		return auditLogSearchCriteriaDao.save(entity);
	}

	public AuditLogSearchCriteria getById(long id) {
		return auditLogSearchCriteriaDao.findById(id).orElse(null);
	}

	public void delete(AuditLogSearchCriteria entity) {
		auditLogSearchCriteriaDao.delete(entity);
	}

	public List<AuditLogSearchCriteria> findAll() {
		return auditLogSearchCriteriaDao.findAll();
	}
}
