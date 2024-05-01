package dk.digitalidentity.common.dao;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.common.dao.model.MessageQueue;

public interface MessageQueueDao extends JpaRepository<MessageQueue, Long> {
	List<MessageQueue> findTop10ByDeliveryTtsBeforeAndEmailNotNull(LocalDateTime tts);
	List<MessageQueue> findTop10ByDeliveryTtsBeforeAndCprNotNull(LocalDateTime tts);
	long countByOperatorApprovedFalse();
	
	// no indexes on these, so behave when calling
	List<MessageQueue> findByCpr(String cpr);
	List<MessageQueue> findByEmail(String email);
}
