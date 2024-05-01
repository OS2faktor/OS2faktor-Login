package dk.digitalidentity.common.dao;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.common.dao.model.Keystore;

public interface KeystoreDao extends JpaRepository<Keystore, Long> {
	Keystore findById(long id);
	List<Keystore> findAll();
	Keystore findByPrimaryForNemLoginTrue();
	Keystore findByPrimaryForNemLoginFalse();
	Keystore findByPrimaryForIdpTrue();
	Keystore findByPrimaryForIdpFalse();
	List<Keystore> findByLastUpdatedAfter(LocalDateTime tts);
}
