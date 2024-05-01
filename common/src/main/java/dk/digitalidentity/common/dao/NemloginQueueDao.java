package dk.digitalidentity.common.dao;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.common.dao.model.NemloginQueue;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NemloginAction;

public interface NemloginQueueDao extends JpaRepository<NemloginQueue, Long> {
	NemloginQueue findById(long id);
	List<NemloginQueue> findByPersonAndAction(Person person, NemloginAction action);
	List<NemloginQueue> findByFailedFalse();
	List<NemloginQueue> findAll();
	void deleteByPersonAndFailedTrue(Person person);
	void deleteByFailedTrueAndTtsBefore(LocalDateTime tts);
}
