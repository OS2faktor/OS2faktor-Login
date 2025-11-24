package dk.digitalidentity.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dk.digitalidentity.common.dao.model.enums.ApiRole;
import dk.digitalidentity.common.security.ApiSecurityFilter;
import dk.digitalidentity.common.service.ClientService;

@Configuration
public class ApiSecurityFilterConfiguration {

	@Autowired
	private ClientService clientService;

	@Bean
	public FilterRegistrationBean<ApiSecurityFilter> internalApiSecurityFilter() {
		ApiSecurityFilter filter = new ApiSecurityFilter();
		filter.setClientService(clientService);
		filter.setApiRole(ApiRole.INTERNAL);
		filter.setEnabled(true);

		FilterRegistrationBean<ApiSecurityFilter> filterRegistrationBean = new FilterRegistrationBean<>(filter);
		filterRegistrationBean.setName("InternalApiSecurityBean");
		filterRegistrationBean.addUrlPatterns("/api/internal", "/api/internal/*");

		return filterRegistrationBean;
	}
}
