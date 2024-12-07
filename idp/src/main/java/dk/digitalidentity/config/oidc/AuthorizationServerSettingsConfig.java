package dk.digitalidentity.config.oidc;

import dk.digitalidentity.config.OS2faktorConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;

@Configuration
public class AuthorizationServerSettingsConfig {

	/**
	 * Contains the configuration settings for the OAuth2 authorization server.
	 * Specifies the URI for the protocol endpoints as well as the issuer identifier.
	 * Defaults are set by the builder, we just need to specify an Issuer
	 */
	@Bean
	public AuthorizationServerSettings authorizationServerSettings(OS2faktorConfiguration configuration) {
		return AuthorizationServerSettings.builder()
				.issuer(configuration.getEntityId())
				.build();
	}
}
