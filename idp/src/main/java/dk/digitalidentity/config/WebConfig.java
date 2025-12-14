package dk.digitalidentity.config;

import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/webjars/**")
				.addResourceLocations("classpath:/META-INF/resources/webjars/")
			    .setCacheControl(CacheControl.maxAge(1, TimeUnit.DAYS));
		
        registry.addResourceHandler("/**")
		        .addResourceLocations("classpath:/static/")
		        .setCacheControl(CacheControl.maxAge(1, TimeUnit.HOURS));
	}
}
