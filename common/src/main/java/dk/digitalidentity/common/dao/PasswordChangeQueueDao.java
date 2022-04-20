package dk.digitalidentity.common.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.common.dao.model.PasswordChangeQueue;
import dk.digitalidentity.common.dao.model.enums.ReplicationStatus;

public interface PasswordChangeQueueDao extends JpaRepository<PasswordChangeQueue, Long> {
	List<PasswordChangeQueue> findAll();
	List<PasswordChangeQueue> findByStatus(ReplicationStatus replicationStatus);
	List<PasswordChangeQueue> findByStatusNotIn(ReplicationStatus... replicationStatus);
	List<PasswordChangeQueue> findBySamaccountNameAndDomainAndStatusNot(String samaccountName, String domain, ReplicationStatus replicationStatus);
	List<PasswordChangeQueue> findByDomain(String domain);
	PasswordChangeQueue findFirst1ByDomainAndStatusOrderByTtsAsc(String domain, ReplicationStatus replicationStatus);
}
