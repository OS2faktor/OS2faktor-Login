package dk.digitalidentity.common.config;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dk.digitalidentity.common.filter.SameSiteFilter;
import jakarta.servlet.DispatcherType;

@Configuration
public class SameSiteFilterConfiguration {

	@Bean
	public FilterRegistrationBean<SameSiteFilter> sameSiteFilter() {
	    List<String> urlPatterns = new ArrayList<>();
	    urlPatterns.add("/*");

		FilterRegistrationBean<SameSiteFilter> registration = new FilterRegistrationBean<>();
		registration.setFilter(new SameSiteFilter());
		registration.setDispatcherTypes(EnumSet.allOf(DispatcherType.class));
		registration.setUrlPatterns(urlPatterns);

		return registration;
	}
}
