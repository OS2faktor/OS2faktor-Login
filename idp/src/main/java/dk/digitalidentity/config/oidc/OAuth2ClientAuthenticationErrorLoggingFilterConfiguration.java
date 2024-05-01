package dk.digitalidentity.config.oidc;

import dk.digitalidentity.filter.Oauth2ClientAuthenticationErrorLoggingFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.ProviderSettings;

@Configuration
public class OAuth2ClientAuthenticationErrorLoggingFilterConfiguration {

	@Autowired
	private ProviderSettings providerSettings;

	@Autowired
	private RegisteredClientRepository registeredClientRepository;

	@Bean
	public FilterRegistrationBean<Oauth2ClientAuthenticationErrorLoggingFilter> Oauth2ClientAuthenticationErrorLoggingFilterConfig() {
		Oauth2ClientAuthenticationErrorLoggingFilter oauth2ClientAuthenticationErrorLoggingFilter = new Oauth2ClientAuthenticationErrorLoggingFilter();
		oauth2ClientAuthenticationErrorLoggingFilter.setRegisteredClientRepository(registeredClientRepository);
		FilterRegistrationBean<Oauth2ClientAuthenticationErrorLoggingFilter> filterRegistrationBean = new FilterRegistrationBean<>(oauth2ClientAuthenticationErrorLoggingFilter);
		
		// default: "/oauth2/token"
		filterRegistrationBean.addUrlPatterns(providerSettings.getTokenEndpoint());

		// just before the filter that authenticates clients (SPs)
		filterRegistrationBean.setOrder(1209);

		return filterRegistrationBean;
	}
}
