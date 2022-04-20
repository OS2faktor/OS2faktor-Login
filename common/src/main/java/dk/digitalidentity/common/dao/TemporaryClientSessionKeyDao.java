package dk.digitalidentity.common.dao;

import dk.digitalidentity.common.dao.model.TemporaryClientSessionKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface TemporaryClientSessionKeyDao extends JpaRepository<TemporaryClientSessionKey, Long> {
	TemporaryClientSessionKey getById(Long id);
	TemporaryClientSessionKey findBySessionKey(String sessionKey);
	List<TemporaryClientSessionKey> findByTtsBefore(LocalDateTime before);
}
