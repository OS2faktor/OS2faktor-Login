package dk.digitalidentity.config.oidc;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.authorization.config.ProviderSettings;
import org.springframework.security.oauth2.server.authorization.web.NimbusJwkSetEndpointFilter;

@Configuration
public class OAuth2NimbusJwkSetEndpointFilterConfiguration {

	@Autowired
	private ProviderSettings providerSettings;

	@Autowired
	private JWKSource<SecurityContext> jwkSource;

	/**
	 * Configures a filter which handles exposing the public keys used for signing JWTs (default location: /oauth2/jwks)"
	 */
	@Bean
	public FilterRegistrationBean<NimbusJwkSetEndpointFilter> OAuth2NimbusJwkSetEndpointFilter() {

		NimbusJwkSetEndpointFilter filter = new NimbusJwkSetEndpointFilter(jwkSource, providerSettings.getJwkSetEndpoint());
		FilterRegistrationBean<NimbusJwkSetEndpointFilter> filterRegistrationBean = new FilterRegistrationBean<>(filter);
		filterRegistrationBean.addUrlPatterns(providerSettings.getJwkSetEndpoint());

		return filterRegistrationBean;
	}
}
