package dk.digitalidentity.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.radius.OS2faktorRadiusServer;
import dk.digitalidentity.service.OS2faktorRadiusService;

@Configuration
public class OS2faktorRadiusServerConfiguration {
	private List<OS2faktorRadiusServer> servers = new ArrayList<>();

	@Autowired
	private CommonConfiguration commonConfiguration;
	
	@Autowired
	private OS2faktorRadiusService os2faktorRadiusService;
	
	@EventListener(ApplicationReadyEvent.class)
	public void init() {
		if (commonConfiguration.getRadiusConfiguration().isEnabled()) {
			servers.add(new OS2faktorRadiusServer(1812, false, os2faktorRadiusService, commonConfiguration.getRadiusConfiguration()));
			servers.add(new OS2faktorRadiusServer(1813, true, os2faktorRadiusService, commonConfiguration.getRadiusConfiguration()));
		}
	}
}
