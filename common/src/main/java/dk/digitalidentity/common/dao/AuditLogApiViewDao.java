package dk.digitalidentity.common.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import dk.digitalidentity.common.dao.model.AuditLogApiView;

public interface AuditLogApiViewDao extends JpaRepository<AuditLogApiView, Long> {

	@Query(value = "SELECT max(id) FROM AuditLogApiView")
	long getMaxId();

	@Query(value = "SELECT * FROM view_audit_log_api a WHERE a.id > ?1 ORDER BY a.id ASC LIMIT ?2", nativeQuery = true)
	public List<AuditLogApiView> findAllWithOffsetAndSize(long offset, long size);

}