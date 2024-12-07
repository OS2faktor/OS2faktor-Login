package dk.digitalidentity.config.oidc.logging;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;

import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.filter.JwtAuditLogFilter;

@Configuration
public class JWTAuditloggerFilterConfiguration {

	@Autowired
	private AuthorizationServerSettings providerSettings;

	@Autowired
	private AuditLogger auditLogger;
	
	@Autowired
	private OAuth2AuthorizationService authorizationService;

	@Autowired
	private PersonService personService;

	@Bean
	public FilterRegistrationBean<JwtAuditLogFilter> jwtAuditLogFilterConfig() {
		JwtAuditLogFilter clientAuthenticationFilter = new JwtAuditLogFilter();
		clientAuthenticationFilter.setAuditLogger(auditLogger);
		clientAuthenticationFilter.setAuthorizationService(authorizationService);
		clientAuthenticationFilter.setPersonService(personService);

		FilterRegistrationBean<JwtAuditLogFilter> filterRegistrationBean = new FilterRegistrationBean<>(clientAuthenticationFilter);
		filterRegistrationBean.addUrlPatterns(providerSettings.getTokenEndpoint()); 				// default: "/oauth2/token"
		filterRegistrationBean.setOrder(1210);

		return filterRegistrationBean;
	}
}
