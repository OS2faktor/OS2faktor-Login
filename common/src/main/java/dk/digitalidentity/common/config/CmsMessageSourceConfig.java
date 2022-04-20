package dk.digitalidentity.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dk.digitalidentity.common.service.CmsMessageSource;

@Configuration
public class CmsMessageSourceConfig {

	@Bean
	public CmsMessageSource cmsMessageSource() {
		CmsMessageSource messageSource = new CmsMessageSource();
		messageSource.setBasename("classpath:cms-messages");
		messageSource.setDefaultEncoding("UTF-8");

		return messageSource;
	}
}
