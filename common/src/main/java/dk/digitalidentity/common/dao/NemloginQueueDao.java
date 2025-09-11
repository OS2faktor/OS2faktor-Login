package dk.digitalidentity.common.dao;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

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
	
	@Modifying
	@Query(nativeQuery = true, value = """
 INSERT INTO nemlogin_queue (person_id, nemlogin_user_uuid, action)
 SELECT p.id, p.nemlogin_user_uuid, 'DELETE'
  FROM persons p
  JOIN person_statistics ps ON ps.person_id = p.id
  JOIN mitid_erhverv_cache m ON m.uuid = p.nemlogin_user_uuid
  WHERE p.locked_dataset = 1
  AND p.nemlogin_user_uuid IS NOT NULL
  AND (ps.last_login IS NULL OR ps.last_login < CURRENT_TIMESTAMP - INTERVAL 3 MONTH)
			""")
	void deleteOldEntries();
}
