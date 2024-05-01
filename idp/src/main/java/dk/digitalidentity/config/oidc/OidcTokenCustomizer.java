package dk.digitalidentity.config.oidc;

import java.util.Map;

import org.springframework.security.oauth2.server.authorization.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class OidcTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

	@SuppressWarnings("deprecation")
	@Override
	public void customize(JwtEncodingContext context) {
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
		
		for (Map.Entry<String, String> entry : claims.entrySet()) {
			context.getClaims().claim(entry.getKey(), entry.getValue());
		}
	}
}
