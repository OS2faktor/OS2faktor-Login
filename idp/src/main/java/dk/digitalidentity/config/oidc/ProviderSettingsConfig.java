package dk.digitalidentity.config.oidc;

import dk.digitalidentity.config.OS2faktorConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.authorization.config.ProviderSettings;

@Configuration
public class ProviderSettingsConfig {

	@Autowired
	private OS2faktorConfiguration configuration;

	/**
	 * ProviderSettings is used by OIDC. it contains the "EntityId" of the "IdP"
	 * and configures on which URLs should be used for login and so on
	 */
	@Bean
	public ProviderSettings providerSettings() {
		return ProviderSettings.builder()
				.issuer(configuration.getEntityId())
				.build();
	}
}
