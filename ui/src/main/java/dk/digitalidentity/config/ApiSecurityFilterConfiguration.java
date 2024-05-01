package dk.digitalidentity.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.security.AuditlogApiSecurityFilter;
import dk.digitalidentity.security.CertManagerApiSecurityFilter;
import dk.digitalidentity.security.CoreDataApiSecurityFilter;
import dk.digitalidentity.security.MfaApiSecurityFilter;
import dk.digitalidentity.security.StilApiSecurityFilter;
import dk.digitalidentity.security.UserAdminstrationApiSecurityFilter;

@Configuration
public class ApiSecurityFilterConfiguration {

	@Autowired
	private OS2faktorConfiguration configuration;
	
	@Autowired
	private CommonConfiguration commonConfiguration;

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
	public FilterRegistrationBean<UserAdminstrationApiSecurityFilter> userAdminstrationApiSecurityFilter() {
		UserAdminstrationApiSecurityFilter filter = new UserAdminstrationApiSecurityFilter();
		filter.setConfiguration(configuration);

		FilterRegistrationBean<UserAdminstrationApiSecurityFilter> filterRegistrationBean = new FilterRegistrationBean<>(filter);
		filterRegistrationBean.addUrlPatterns("/api/userAdminstration", "/api/userAdminstration/*");

		return filterRegistrationBean;
	}
	
	@Bean
	public FilterRegistrationBean<CertManagerApiSecurityFilter> certManagerApiSecurityFilter() {
		CertManagerApiSecurityFilter filter = new CertManagerApiSecurityFilter();
		filter.setConfiguration(configuration);

		FilterRegistrationBean<CertManagerApiSecurityFilter> filterRegistrationBean = new FilterRegistrationBean<>(filter);
		filterRegistrationBean.addUrlPatterns("/api/certmanager", "/api/certmanager/*");

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
	
	@Bean
	public FilterRegistrationBean<StilApiSecurityFilter> stilApiSecurityFilter() {
		StilApiSecurityFilter filter = new StilApiSecurityFilter();
		filter.setConfiguration(commonConfiguration);

		FilterRegistrationBean<StilApiSecurityFilter> filterRegistrationBean = new FilterRegistrationBean<>(filter);
		filterRegistrationBean.addUrlPatterns("/api/stil", "/api/stil/*");

		return filterRegistrationBean;
	}
}
