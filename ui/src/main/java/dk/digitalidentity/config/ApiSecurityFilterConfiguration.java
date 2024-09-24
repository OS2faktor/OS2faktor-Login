package dk.digitalidentity.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.enums.ApiRole;
import dk.digitalidentity.common.service.ClientService;
import dk.digitalidentity.security.ApiSecurityFilter;

@Configuration
public class ApiSecurityFilterConfiguration {

	@Autowired
	private OS2faktorConfiguration configuration;
	
	@Autowired
	private CommonConfiguration commonConfiguration;

	@Autowired
	private ClientService clientService;

	@Bean
	public FilterRegistrationBean<ApiSecurityFilter> coreDataApiSecurityFilter() {
		ApiSecurityFilter filter = new ApiSecurityFilter();
		filter.setApiRole(ApiRole.COREDATA);
		filter.setClientService(clientService);
		filter.setEnabled(true);

		FilterRegistrationBean<ApiSecurityFilter> filterRegistrationBean = new FilterRegistrationBean<>(filter);
		filterRegistrationBean.setName("CoreDataAPISecurityFilterBean");
		filterRegistrationBean.addUrlPatterns("/api/coredata", "/api/coredata/*");

		return filterRegistrationBean;
	}

	@Bean
	public FilterRegistrationBean<ApiSecurityFilter> auditLogApiSecurityFilter() {
		ApiSecurityFilter filter = new ApiSecurityFilter();
		filter.setApiRole(ApiRole.AUDITLOG);
		filter.setClientService(clientService);
		filter.setEnabled(true);

		FilterRegistrationBean<ApiSecurityFilter> filterRegistrationBean = new FilterRegistrationBean<>(filter);
		filterRegistrationBean.setName("AuditLogAPISecurityBean");
		filterRegistrationBean.addUrlPatterns("/api/auditlog", "/api/auditlog/*");

		return filterRegistrationBean;
	}

	@Bean
	public FilterRegistrationBean<ApiSecurityFilter> userAdministrationApiSecurityFilter() {
		ApiSecurityFilter filter = new ApiSecurityFilter();
		filter.setEnabled(configuration.getUserAdministration().isEnabled());
		filter.setApiRole(ApiRole.USERADMINISTRATION);
		filter.setClientService(clientService);

		FilterRegistrationBean<ApiSecurityFilter> filterRegistrationBean = new FilterRegistrationBean<>(filter);
		filterRegistrationBean.addUrlPatterns("/api/userAdministration", "/api/userAdministration/*");
		filterRegistrationBean.setName("UserAdministrationAPISecurityFilterBean");

		return filterRegistrationBean;
	}

	@Bean
	public FilterRegistrationBean<ApiSecurityFilter> certManagerApiSecurityFilter() {
		ApiSecurityFilter filter = new ApiSecurityFilter();
		filter.setEnabled(configuration.getCertManagerApi().isEnabled());
		filter.setApiRole(ApiRole.CERTMANAGER);
		filter.setClientService(clientService);

		FilterRegistrationBean<ApiSecurityFilter> filterRegistrationBean = new FilterRegistrationBean<>(filter);
		filterRegistrationBean.addUrlPatterns("/api/certmanager", "/api/certmanager/*");
		filterRegistrationBean.setName("CertManagerAPISecurityFilterBean");

		return filterRegistrationBean;
	}

	@Bean
	public FilterRegistrationBean<ApiSecurityFilter> mfaApiSecurityFilter() {
		ApiSecurityFilter filter = new ApiSecurityFilter();
		filter.setClientService(clientService);
		filter.setApiRole(ApiRole.MFA);
		filter.setEnabled(true);

		FilterRegistrationBean<ApiSecurityFilter> filterRegistrationBean = new FilterRegistrationBean<>(filter);
		filterRegistrationBean.addUrlPatterns("/api/mfa", "/api/mfa/*");
		filterRegistrationBean.setName("MFAAPISecurityFilterBean");

		return filterRegistrationBean;
	}

	@Bean
	public FilterRegistrationBean<ApiSecurityFilter> stilApiSecurityFilter() {
		ApiSecurityFilter filter = new ApiSecurityFilter();
		filter.setClientService(clientService);
		filter.setApiRole(ApiRole.STIL);
		filter.setEnabled(commonConfiguration.getStilStudent().isEnabled());

		FilterRegistrationBean<ApiSecurityFilter> filterRegistrationBean = new FilterRegistrationBean<>(filter);
		filterRegistrationBean.addUrlPatterns("/api/stil", "/api/stil/*");
		filterRegistrationBean.setName("StilAPISecurityFilterBean");

		return filterRegistrationBean;
	}

	@Bean
	public FilterRegistrationBean<ApiSecurityFilter> hardWareTokenApiSecurityFilter() {
		ApiSecurityFilter filter = new ApiSecurityFilter();
		filter.setClientService(clientService);
		filter.setApiRole(ApiRole.HARDWARETOKEN);
		filter.setEnabled(true);

		FilterRegistrationBean<ApiSecurityFilter> filterRegistrationBean = new FilterRegistrationBean<>(filter);
		filterRegistrationBean.setName("HardWareTokenAPISecurityBean");
		filterRegistrationBean.addUrlPatterns("/api/kodeviser", "/api/kodeviser/*");

		return filterRegistrationBean;
	}

	@Bean
	public FilterRegistrationBean<ApiSecurityFilter> passwordChangeQueueApiSecurityFilter() {
		ApiSecurityFilter filter = new ApiSecurityFilter();
		filter.setClientService(clientService);
		filter.setApiRole(ApiRole.PASSWORD_CHANGE_QUEUE);
		filter.setEnabled(configuration.getPasswordChangeQueueApi().isEnabled());

		FilterRegistrationBean<ApiSecurityFilter> filterRegistrationBean = new FilterRegistrationBean<>(filter);
		filterRegistrationBean.setName("PasswordChangeQueueApiSecuritBean");
		filterRegistrationBean.addUrlPatterns("/api/passwordqueue", "/api/passwordqueue/*");

		return filterRegistrationBean;
	}
}
