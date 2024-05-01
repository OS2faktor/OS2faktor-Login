package dk.digitalidentity.common.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.common.dao.model.AuditLogSearchCriteria;

public interface AuditLogSearchCriteriaDao extends JpaRepository<AuditLogSearchCriteria, Long> {

}
