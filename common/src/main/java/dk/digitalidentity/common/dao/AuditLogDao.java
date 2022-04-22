package dk.digitalidentity.common.dao;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import dk.digitalidentity.common.dao.model.AuditLog;
import dk.digitalidentity.common.dao.model.enums.LogAction;

public interface AuditLogDao extends JpaRepository<AuditLog, Long> {
	AuditLog getById(Long id);
	List<AuditLog> findByCpr(String cpr);
	List<AuditLog> findByCorrelationId(String id);
	List<AuditLog> findByTtsAfterAndTtsBeforeAndLogAction(LocalDateTime after, LocalDateTime before, LogAction logAction);
	List<AuditLog> findAllByTtsAfter(LocalDateTime after);
	List<AuditLog> findByPersonDomainAndTtsAfter(String personDomain, LocalDateTime after);
	List<AuditLog> findByTtsAfterAndLogActionIn(LocalDateTime tts, LogAction... actions);
	List<AuditLog> findByLogActionIn(LogAction... actions);
	
	@Modifying
	@Query(nativeQuery = true, value = "DELETE FROM auditlogs WHERE tts < ?1")
	void deleteByTtsBefore(LocalDateTime before);
}
