package dk.digitalidentity.common.dao;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.common.dao.model.Keystore;

public interface KeystoreDao extends JpaRepository<Keystore, Long> {
	Keystore findById(long id);
	List<Keystore> findAll();
	List<Keystore> findByLastUpdatedAfter(LocalDateTime tts);
	Keystore findByAlias(String alias);
}
