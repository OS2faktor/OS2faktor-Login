package dk.digitalidentity.common.dao;

import dk.digitalidentity.common.dao.model.enums.ReplicationStatus;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.common.dao.model.PasswordChangeQueue;

public interface PasswordChangeQueueDao extends JpaRepository<PasswordChangeQueue, Long> {
	List<PasswordChangeQueue> findAll();
	List<PasswordChangeQueue> findByStatus(ReplicationStatus replicationStatus);
	List<PasswordChangeQueue> findByStatusNot(ReplicationStatus replicationStatus);
	PasswordChangeQueue findFirst1ByDomainAndStatusNotOrderByTtsAsc(String domain, ReplicationStatus replicationStatus);
}
