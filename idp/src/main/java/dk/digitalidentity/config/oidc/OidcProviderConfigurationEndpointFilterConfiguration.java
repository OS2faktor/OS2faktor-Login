package dk.digitalidentity.config.oidc;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.authorization.config.ProviderSettings;
import org.springframework.security.oauth2.server.authorization.oidc.web.OidcProviderConfigurationEndpointFilter;

@Configuration
public class OidcProviderConfigurationEndpointFilterConfiguration {

	@Autowired
	private ProviderSettings providerSettings;

	/**
	 * Configures a filter which handles one of the two OIDC/OAuth2 "Metadata" endpoints located under "/.well-known/..."
 	 */
	@Bean
	public FilterRegistrationBean<OidcProviderConfigurationEndpointFilter> oidcProviderConfigurationEndpointFilter() {
		OidcProviderConfigurationEndpointFilter filter = new OidcProviderConfigurationEndpointFilter(providerSettings);
		FilterRegistrationBean<OidcProviderConfigurationEndpointFilter> filterRegistrationBean = new FilterRegistrationBean<>(filter);
		filterRegistrationBean.addUrlPatterns("/.well-known/openid-configuration");

		return filterRegistrationBean;
	}
}
