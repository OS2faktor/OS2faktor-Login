package dk.digitalidentity.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dk.digitalidentity.common.service.WindowCredentialProviderClientService;
import dk.digitalidentity.filter.ClientApiSecurityFilter;

@Configuration
public class ClientApiSecurityFilterConfiguration {

	@Autowired
	private WindowCredentialProviderClientService clientService;

	@Bean
	public FilterRegistrationBean<ClientApiSecurityFilter> apiSecurityFilter() {
		ClientApiSecurityFilter filter = new ClientApiSecurityFilter();
		filter.setWindowsClientService(clientService);

		FilterRegistrationBean<ClientApiSecurityFilter> filterRegistrationBean = new FilterRegistrationBean<>(filter);
		filterRegistrationBean.addUrlPatterns("/api/client/login");
		filterRegistrationBean.addUrlPatterns("/api/client/loginWithBody");
		filterRegistrationBean.addUrlPatterns("/api/client/changePassword");
		filterRegistrationBean.addUrlPatterns("/api/client/changePasswordWithBody");

		return filterRegistrationBean;
	}
}
