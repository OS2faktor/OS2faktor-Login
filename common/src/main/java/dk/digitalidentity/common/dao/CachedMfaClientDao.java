package dk.digitalidentity.common.dao;

import dk.digitalidentity.common.service.mfa.model.ClientType;
import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.common.dao.model.CachedMfaClient;

import java.util.List;

public interface CachedMfaClientDao extends JpaRepository<CachedMfaClient, Long> {

	void deleteBySerialnumber(String serial);

	List<CachedMfaClient> findByType(ClientType type);
}
