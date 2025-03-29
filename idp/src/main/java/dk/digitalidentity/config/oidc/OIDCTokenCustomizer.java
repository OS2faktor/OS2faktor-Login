package dk.digitalidentity.config.oidc;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class OIDCTokenCustomizer {
	@Bean
	public OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer() {
		return (context) -> {
			if (log.isDebugEnabled()) {
				log.debug("JWT token customizer called");
				log.debug("JWT token type: {}", context.getTokenType().getValue());
			}
			if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType()) || new OAuth2TokenType("id_token").equals(context.getTokenType())) {
				OAuth2Authorization authorization = context.getAuthorization();
				if (authorization == null) {
					log.warn("Could not get authorization, no custom claims were added");
					return;
				}

				Map<String, String> claims = authorization.getAttribute("ComputedClaims");
				if (claims == null) {
					log.warn("No claims supplied");
					return;
				}

				JwtClaimsSet.Builder contextClaims = context.getClaims();
				for (Map.Entry<String, String> entry : claims.entrySet()) {
					contextClaims.claim(entry.getKey(), entry.getValue());
				}
			}
		};
	}
}
