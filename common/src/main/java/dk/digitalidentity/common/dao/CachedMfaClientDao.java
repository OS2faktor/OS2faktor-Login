package dk.digitalidentity.common.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.common.dao.model.CachedMfaClient;

public interface CachedMfaClientDao extends JpaRepository<CachedMfaClient, Long> {

	void deleteBySerialnumber(String serial);

}
