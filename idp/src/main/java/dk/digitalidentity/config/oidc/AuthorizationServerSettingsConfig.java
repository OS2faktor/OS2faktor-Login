package dk.digitalidentity.config.oidc;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;

import dk.digitalidentity.config.OS2faktorConfiguration;

@Configuration
public class AuthorizationServerSettingsConfig {

	@Bean
	public AuthorizationServerSettings authorizationServerSettings(OS2faktorConfiguration configuration) {
		return AuthorizationServerSettings.builder()
				.issuer(configuration.getEntityId())
				.build();
	}
}
