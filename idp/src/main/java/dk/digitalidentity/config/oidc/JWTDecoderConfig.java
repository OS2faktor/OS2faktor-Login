package dk.digitalidentity.config.oidc;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;

@Configuration
public class JWTDecoderConfig {

	/**
	 * This JWTDecoder is REQUIRED for the OpenID Connect 1.0 UserInfo endpoint and the OpenID Connect 1.0 Client Registration endpoint.
	 * We do not use the Client Registration endpoint (disabled) but we do support UserInfo
	 */
	@Bean
	public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
		return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
	}
}
