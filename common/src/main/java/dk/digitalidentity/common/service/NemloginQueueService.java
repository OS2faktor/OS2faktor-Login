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
	
	public NemloginQueue save(NemloginQueue nemloginQueue) {
		return nemloginQueueDao.save(nemloginQueue);
	}
	
	public List<NemloginQueue> saveAll(List<NemloginQueue> nemloginQueues) {
		return nemloginQueueDao.saveAll(nemloginQueues);
	}
	
	public void deleteFailedByPerson(Person person) {
		nemloginQueueDao.deleteByPersonAndFailedTrue(person);
	}
	
	public List<NemloginQueue> getByPersonAndAction(Person person, NemloginAction action) {
		return nemloginQueueDao.findByPersonAndAction(person, action);
	}

	public List<NemloginQueue> getAllNotFailed() {
		return nemloginQueueDao.findByFailedFalse();
	}

	public List<NemloginQueue> getAll() {
		return nemloginQueueDao.findAll();
	}

	public void deleteAll(List<NemloginQueue> toDelete) {
		nemloginQueueDao.deleteAll(toDelete);
	}

	public NemloginQueue getById(long queueId) {
		return nemloginQueueDao.findById(queueId);
	}

	@Transactional
	public void cleanupOldFailedEntries() {
		LocalDateTime daysAgo = LocalDateTime.now().minusDays(7);
		nemloginQueueDao.deleteByFailedTrueAndTtsBefore(daysAgo);
	}
}
