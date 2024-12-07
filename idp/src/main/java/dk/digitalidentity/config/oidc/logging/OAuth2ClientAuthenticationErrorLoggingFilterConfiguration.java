package dk.digitalidentity.config.oidc.logging;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;

import dk.digitalidentity.filter.OAuth2ClientAuthenticationErrorLoggingFilter;

@Configuration
public class OAuth2ClientAuthenticationErrorLoggingFilterConfiguration {

	@Autowired
	private AuthorizationServerSettings providerSettings;

	@Autowired
	private RegisteredClientRepository registeredClientRepository;

	@Bean
	public FilterRegistrationBean<OAuth2ClientAuthenticationErrorLoggingFilter> Oauth2ClientAuthenticationErrorLoggingFilterConfig() {
		OAuth2ClientAuthenticationErrorLoggingFilter oauth2ClientAuthenticationErrorLoggingFilter = new OAuth2ClientAuthenticationErrorLoggingFilter();
		oauth2ClientAuthenticationErrorLoggingFilter.setRegisteredClientRepository(registeredClientRepository);
		FilterRegistrationBean<OAuth2ClientAuthenticationErrorLoggingFilter> filterRegistrationBean = new FilterRegistrationBean<>(oauth2ClientAuthenticationErrorLoggingFilter);
		
		// default: "/oauth2/token"
		filterRegistrationBean.addUrlPatterns(providerSettings.getTokenEndpoint());

		// just before the filter that authenticates clients (SPs)
		filterRegistrationBean.setOrder(1209);

		return filterRegistrationBean;
	}
}
