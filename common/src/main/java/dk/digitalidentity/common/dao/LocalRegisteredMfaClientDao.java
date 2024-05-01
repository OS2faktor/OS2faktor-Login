package dk.digitalidentity.common.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.common.dao.model.LocalRegisteredMfaClient;

public interface LocalRegisteredMfaClientDao extends JpaRepository<LocalRegisteredMfaClient, Long> {
	List<LocalRegisteredMfaClient> findByCpr(String cpr);

	List<LocalRegisteredMfaClient> findByDeviceId(String deviceId);
}
