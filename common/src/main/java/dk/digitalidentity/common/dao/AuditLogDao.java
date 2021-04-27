package dk.digitalidentity.common.dao;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.common.dao.model.AuditLog;
import dk.digitalidentity.common.dao.model.enums.LogAction;

public interface AuditLogDao extends JpaRepository<AuditLog, Long> {
	AuditLog getById(Long id);
	List<AuditLog> findByCpr(String cpr);
	List<AuditLog> findByCorrelationId(String id);
	List<AuditLog> findByTtsAfterAndTtsBeforeAndLogAction(LocalDateTime after, LocalDateTime before, LogAction logAction);
	List<AuditLog> findAllByTtsAfter(LocalDateTime after);
	List<AuditLog> findByPersonDomainAndTtsAfter(String personDomain, LocalDateTime after);
}
