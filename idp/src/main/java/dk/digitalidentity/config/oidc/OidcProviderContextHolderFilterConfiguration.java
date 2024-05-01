package dk.digitalidentity.config.oidc;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.authorization.config.ProviderSettings;
import org.springframework.security.oauth2.server.authorization.web.ProviderContextFilter;

@Configuration
public class OidcProviderContextHolderFilterConfiguration {

	@Autowired
	private ProviderSettings providerSettings;

	/**
	 * Configures a filter which handles one of the two OIDC/OAuth2 "Metadata" endpoints located under "/.well-known/..."
 	 */
	@Bean
	public FilterRegistrationBean<ProviderContextFilter> oidcProviderContextHolderFilter() {
		ProviderContextFilter filter = new ProviderContextFilter(providerSettings);
		FilterRegistrationBean<ProviderContextFilter> filterRegistrationBean = new FilterRegistrationBean<>(filter);
		filterRegistrationBean.addUrlPatterns("/oauth2/authorize");
		filterRegistrationBean.addUrlPatterns("/oauth2/introspect");
		filterRegistrationBean.addUrlPatterns("/oauth2/jwks");
		filterRegistrationBean.addUrlPatterns("/oauth2/revoke");
		filterRegistrationBean.addUrlPatterns("/oauth2/token");
		filterRegistrationBean.addUrlPatterns("/userinfo");
		filterRegistrationBean.addUrlPatterns("/.well-known/oauth-authorization-server");
		filterRegistrationBean.addUrlPatterns("/.well-known/openid-configuration");

		filterRegistrationBean.setOrder(410);

		return filterRegistrationBean;
	}
}
