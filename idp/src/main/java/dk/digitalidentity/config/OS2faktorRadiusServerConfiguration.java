package dk.digitalidentity.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.radius.OS2faktorRadiusServer;
import dk.digitalidentity.service.OS2faktorRadiusService;

@Configuration
public class OS2faktorRadiusServerConfiguration {

	@Autowired
	private CommonConfiguration commonConfiguration;
	
	@Autowired
	private OS2faktorRadiusService os2faktorRadiusService;
	
	@Bean(name = "radiusServerWithoutMfa")
	@Lazy(false)
	public OS2faktorRadiusServer radiusServerWithoutMfa() {
		return new OS2faktorRadiusServer(1812, false, os2faktorRadiusService, commonConfiguration.getRadiusConfiguration());
	}

	@Bean(name = "radiusServerWithMfa")
	@Lazy(false)
	public OS2faktorRadiusServer radiusServerWithMfa() {
		return new OS2faktorRadiusServer(1813, true, os2faktorRadiusService, commonConfiguration.getRadiusConfiguration());
	}
}
