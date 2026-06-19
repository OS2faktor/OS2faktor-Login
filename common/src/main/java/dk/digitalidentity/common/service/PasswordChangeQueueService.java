package dk.digitalidentity.common.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.common.dao.PasswordChangeQueueDao;
import dk.digitalidentity.common.dao.model.PasswordChangeQueue;
import dk.digitalidentity.common.dao.model.enums.ReplicationStatus;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PasswordChangeQueueService {
	
	@Autowired
	private PasswordChangeQueueDao passwordChangeQueueDao;

	public PasswordChangeQueue save(PasswordChangeQueue passwordChangeQueue) {
		return save(passwordChangeQueue, true);
	}

	@Transactional // this is OK, need a transaction to save a detached entity (and do extra lookups ;))
	public PasswordChangeQueue save(PasswordChangeQueue passwordChangeQueue, boolean deleteOldEntries) {
		// if the user tries to change password multiple times in a row, we only want to keep the latest - this
		// removes any attempts in the queue that is not already synchronized (which we need to keep for debugging purposes)

		if (deleteOldEntries) {
			List<PasswordChangeQueue> oldQueued = passwordChangeQueueDao.findBySamaccountNameAndDomainAndStatusNot(passwordChangeQueue.getSamaccountName(), passwordChangeQueue.getDomain(), ReplicationStatus.SYNCHRONIZED);
			if (oldQueued != null && oldQueued.size() > 0) {
				// do not delete the current one, it might not have a SYNCHRONIZED status
				oldQueued.removeIf(q -> q.getId() == passwordChangeQueue.getId());
				
				passwordChangeQueueDao.deleteAll(oldQueued);
			}
		}

		return passwordChangeQueueDao.save(passwordChangeQueue);
	}

	public void delete(PasswordChangeQueue passwordChangeQueue) {
		passwordChangeQueueDao.delete(passwordChangeQueue);
	}

	public List<PasswordChangeQueue> getAll() {
		return passwordChangeQueueDao.findAll();
	}

	public List<PasswordChangeQueue> getByStatus(ReplicationStatus status) {
		return passwordChangeQueueDao.findByStatus(status);
	}

	public List<PasswordChangeQueue> getUnsynchronized() {
		return passwordChangeQueueDao.findByStatusNotIn(ReplicationStatus.SYNCHRONIZED, ReplicationStatus.FINAL_ERROR, ReplicationStatus.DO_NOT_REPLICATE);
	}

	public PasswordChangeQueue getOldestUnsynchronizedByDomain(String domain) {
		return passwordChangeQueueDao.findFirst1ByDomainAndStatusOrderByTtsAsc(domain, ReplicationStatus.WAITING_FOR_REPLICATION);
	}

	public List<PasswordChangeQueue> getByDomain(String domain) {
		return passwordChangeQueueDao.findByDomain(domain);
	}

	public List<PasswordChangeQueue> getNotSyncedAzure(String domain) {
		return passwordChangeQueueDao.findByDomainAndStatusInAndAzureReplicatedFalse(domain, ReplicationStatus.DO_NOT_REPLICATE, ReplicationStatus.SYNCHRONIZED);
	}

	public List<PasswordChangeQueue> getNotSyncedGoogleWorkspace(String domain) {
		return passwordChangeQueueDao.findByDomainAndStatusInAndGoogleWorkspaceReplicatedFalse(domain, ReplicationStatus.DO_NOT_REPLICATE, ReplicationStatus.SYNCHRONIZED);
	}

	public void saveAll(List<PasswordChangeQueue> entries) {
		passwordChangeQueueDao.saveAll(entries);
	}
}
