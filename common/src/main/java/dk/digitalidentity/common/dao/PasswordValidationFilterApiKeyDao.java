package dk.digitalidentity.common.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.common.dao.model.PasswordValidationFilterApiKey;

public interface PasswordValidationFilterApiKeyDao extends JpaRepository<PasswordValidationFilterApiKey, Long> {
	PasswordValidationFilterApiKey findByApiKeyAndDisabledFalse(String apiKey);
}