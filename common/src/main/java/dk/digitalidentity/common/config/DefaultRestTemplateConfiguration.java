package dk.digitalidentity.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class DefaultRestTemplateConfiguration {

	@Bean(name = "defaultRestTemplate")
	public RestTemplate defaultRestTemplate() {
		return new RestTemplate();
	}
}
