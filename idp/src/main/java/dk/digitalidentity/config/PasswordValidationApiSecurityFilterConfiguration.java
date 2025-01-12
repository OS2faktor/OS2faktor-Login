package dk.digitalidentity.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dk.digitalidentity.common.service.PasswordValidationFilterApiKeyService;
import dk.digitalidentity.filter.PasswordValidationApiFilter;

@Configuration
public class PasswordValidationApiSecurityFilterConfiguration {

	@Autowired
	private PasswordValidationFilterApiKeyService passwordValidationFilterApiKeyService;

	@Bean
	public FilterRegistrationBean<PasswordValidationApiFilter> passwordValidationApiSecurityFilter() {
		PasswordValidationApiFilter filter = new PasswordValidationApiFilter();
		filter.setPasswordFilterService(passwordValidationFilterApiKeyService);

		FilterRegistrationBean<PasswordValidationApiFilter> filterRegistrationBean = new FilterRegistrationBean<>(filter);
		filterRegistrationBean.addUrlPatterns("/api/password/filter/v1/validate");

		return filterRegistrationBean;
	}
}
