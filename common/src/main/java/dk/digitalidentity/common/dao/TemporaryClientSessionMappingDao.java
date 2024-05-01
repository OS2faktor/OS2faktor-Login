package dk.digitalidentity.common.dao;

import dk.digitalidentity.common.dao.model.TemporaryClientSessionKey;
import dk.digitalidentity.common.dao.model.mapping.TemporaryClientSessionMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TemporaryClientSessionMappingDao extends JpaRepository<TemporaryClientSessionMapping, Long> {
	List<TemporaryClientSessionMapping> findAll();

	List<TemporaryClientSessionMapping> findByTemporaryClientSessionKey(String sessionKey);

	List<TemporaryClientSessionMapping> findByTemporaryClient(TemporaryClientSessionKey temporaryClient);
}
