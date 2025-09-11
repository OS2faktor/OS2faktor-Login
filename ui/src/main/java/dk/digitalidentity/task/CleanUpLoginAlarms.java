package dk.digitalidentity.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.service.LoginAlarmService;
import dk.digitalidentity.config.OS2faktorConfiguration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@EnableScheduling
public class CleanUpLoginAlarms {

	@Autowired
	private OS2faktorConfiguration configuration;

	@Autowired
	private LoginAlarmService loginAlarmService;

	// cleanup in the morning
	@Scheduled(cron = "0 #{new java.util.Random().nextInt(55)} 5 * * *")
	public void cleanUp() {
		if (configuration.getScheduled().isEnabled()) {
			log.info("Running cleanupLoginAlarms");
			
			loginAlarmService.deleteOldCountryAlarms();
			loginAlarmService.deleteOldIpAddressAlarms();
			
			log.info("Running cleanupLoginAlarms ended");
		}
	}
}
