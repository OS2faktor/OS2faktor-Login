package dk.digitalidentity.config.oidc;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.authorization.config.ProviderSettings;
import org.springframework.security.oauth2.server.authorization.web.OAuth2AuthorizationServerMetadataEndpointFilter;

@Configuration
public class OAuth2AuthorizationServerMetadataEndpointFilterConfiguration {

	@Autowired
	private ProviderSettings providerSettings;

	/**
	 * Configures a filter which handles one of the two OIDC/OAuth2 "Metadata" endpoints located under "/.well-known/..."
 	 */
	@Bean
	public FilterRegistrationBean<OAuth2AuthorizationServerMetadataEndpointFilter> oAuth2AuthorizationServerMetadataEndpointFilter() {
		OAuth2AuthorizationServerMetadataEndpointFilter filter = new OAuth2AuthorizationServerMetadataEndpointFilter(providerSettings);
		FilterRegistrationBean<OAuth2AuthorizationServerMetadataEndpointFilter> filterRegistrationBean = new FilterRegistrationBean<>(filter);
		filterRegistrationBean.addUrlPatterns("/.well-known/oauth-authorization-server");

		return filterRegistrationBean;
	}
}
