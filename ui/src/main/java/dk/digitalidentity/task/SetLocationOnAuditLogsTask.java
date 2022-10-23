package dk.digitalidentity.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.service.GeoLocateService;

@Component
@EnableScheduling
public class SetLocationOnAuditLogsTask {
	
	@Autowired
	private OS2faktorConfiguration configuration;
	
	@Autowired
	private GeoLocateService geoLocateService;

	// run every two minutes
	@Scheduled(fixedDelay = 2 * 60 * 1000)
	public void processChanges() {
		if (configuration.getScheduled().isEnabled() && configuration.getGeo().isEnabled()) {
			geoLocateService.setLocationFromIP();
		}
	}
}
