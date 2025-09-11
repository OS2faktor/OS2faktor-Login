package dk.digitalidentity.common.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.common.dao.NemloginQueueDao;
import dk.digitalidentity.common.dao.model.NemloginQueue;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NemloginAction;

@Service
public class NemloginQueueService {
	
	@Autowired
	private NemloginQueueDao nemloginQueueDao;
	
	@Transactional // this is OK, need to save detached entities
	public NemloginQueue save(NemloginQueue nemloginQueue) {
		return nemloginQueueDao.save(nemloginQueue);
	}
	
	@Transactional // this is OK, need to save detached entities
	public List<NemloginQueue> saveAll(List<NemloginQueue> nemloginQueues) {
		return nemloginQueueDao.saveAll(nemloginQueues);
	}
	
	@Transactional
	public void deleteOldEntries() {
		nemloginQueueDao.deleteOldEntries();
	}
	
	@Transactional // this is OK, need to delete like this
	public void deleteFailedByPerson(Person person) {
		nemloginQueueDao.deleteByPersonAndFailedTrue(person);
	}
	
	@Transactional //  this is OK, isolated read
	public List<NemloginQueue> getByPersonAndAction(Person person, NemloginAction action) {
		return nemloginQueueDao.findByPersonAndAction(person, action);
	}

	@Transactional // this is OK, need isolated read
	public List<NemloginQueue> getAllNotFailed() {
		List<NemloginQueue> queue = nemloginQueueDao.findByFailedFalse();
		
		queue.forEach(q -> {
			q.getPerson().getDomain().getName();
		});
		
		return queue;
	}

	public List<NemloginQueue> getAll() {
		return nemloginQueueDao.findAll();
	}

	@Transactional // this is OK - isolated delete
	public void deleteAll(List<NemloginQueue> toDelete) {
		nemloginQueueDao.deleteAll(toDelete);
	}

	public NemloginQueue getById(long queueId) {
		return nemloginQueueDao.findById(queueId);
	}

	@Transactional // this is OK as we use it for isolated SQL operation
	public void cleanupOldFailedEntries() {
		LocalDateTime daysAgo = LocalDateTime.now().minusDays(7);
		nemloginQueueDao.deleteByFailedTrueAndTtsBefore(daysAgo);
	}
}
