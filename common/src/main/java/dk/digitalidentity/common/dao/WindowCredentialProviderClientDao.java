package dk.digitalidentity.common.dao;

import dk.digitalidentity.common.dao.model.WindowCredentialProviderClient;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WindowCredentialProviderClientDao extends JpaRepository<WindowCredentialProviderClient, Long> {
	WindowCredentialProviderClient findById(long id);
	WindowCredentialProviderClient findByApiKeyAndDisabledFalse(String apiKey);
}
