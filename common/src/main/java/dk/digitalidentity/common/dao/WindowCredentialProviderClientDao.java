package dk.digitalidentity.common.dao;

import dk.digitalidentity.common.dao.model.WindowCredentialProviderClient;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WindowCredentialProviderClientDao extends JpaRepository<WindowCredentialProviderClient, Long> {
	WindowCredentialProviderClient getById(Long id);
	WindowCredentialProviderClient findByName(String name);
	WindowCredentialProviderClient findByNameAndDisabledFalse(String name);
}
