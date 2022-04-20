package dk.digitalidentity.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dk.digitalidentity.security.AuditlogApiSecurityFilter;
import dk.digitalidentity.security.CoreDataApiSecurityFilter;
import dk.digitalidentity.security.MfaApiSecurityFilter;

@Configuration
public class ApiSecurityFilterConfiguration {

	@Autowired
	private OS2faktorConfiguration configuration;

	@Bean
	public FilterRegistrationBean<CoreDataApiSecurityFilter> coreDataApiSecurityFilter() {
		CoreDataApiSecurityFilter filter = new CoreDataApiSecurityFilter();
		filter.setConfiguration(configuration);

		FilterRegistrationBean<CoreDataApiSecurityFilter> filterRegistrationBean = new FilterRegistrationBean<>(filter);
		filterRegistrationBean.addUrlPatterns("/api/coredata", "/api/coredata/*");

		return filterRegistrationBean;
	}

	@Bean
	public FilterRegistrationBean<AuditlogApiSecurityFilter> auditLogApiSecurityFilter() {
		AuditlogApiSecurityFilter filter = new AuditlogApiSecurityFilter();
		filter.setConfiguration(configuration);

		FilterRegistrationBean<AuditlogApiSecurityFilter> filterRegistrationBean = new FilterRegistrationBean<>(filter);
		filterRegistrationBean.addUrlPatterns("/api/auditlog", "/api/auditlog/*");

		return filterRegistrationBean;
	}
	
	@Bean
	public FilterRegistrationBean<MfaApiSecurityFilter> mfaApiSecurityFilter() {
		MfaApiSecurityFilter filter = new MfaApiSecurityFilter();
		filter.setConfiguration(configuration);

		FilterRegistrationBean<MfaApiSecurityFilter> filterRegistrationBean = new FilterRegistrationBean<>(filter);
		filterRegistrationBean.addUrlPatterns("/api/mfa", "/api/mfa/*");

		return filterRegistrationBean;
	}
}
