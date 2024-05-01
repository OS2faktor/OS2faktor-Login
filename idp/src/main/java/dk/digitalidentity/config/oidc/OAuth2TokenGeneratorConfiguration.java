package dk.digitalidentity.config.oidc;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.NimbusJwsEncoder;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2RefreshTokenGenerator;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

@SuppressWarnings("deprecation")
@Configuration
public class OAuth2TokenGeneratorConfiguration {

	@Autowired
	private OidcTokenCustomizer oidcTokenCustomizer;

	@Bean
	public JwtGenerator jwtGenerator(JWKSource<SecurityContext> source) {
		// TODO: The NimbusJwsEncoder is deprecated because the next release will use the JwtEncoder from Spring Security v5.6 https://github.com/spring-projects/spring-authorization-server/issues/596
		// 		 Use this until upgrade to spring-authorization-server v0.3.0+
		JwtGenerator jwtGenerator = new JwtGenerator(new NimbusJwsEncoder(source));
		jwtGenerator.setJwtCustomizer(oidcTokenCustomizer);

		return jwtGenerator;
	}

	@Bean
	public DelegatingOAuth2TokenGenerator tokenGenerator(JwtGenerator jwtGenerator) {
		return new DelegatingOAuth2TokenGenerator(jwtGenerator,
				new OAuth2AccessTokenGenerator(), new OAuth2RefreshTokenGenerator());
	}
}
