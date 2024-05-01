package dk.digitalidentity.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.service.ADPasswordService;
import dk.digitalidentity.config.OS2faktorConfiguration;
import lombok.extern.slf4j.Slf4j;

@Component
@EnableScheduling
@Slf4j
public class ADCacheCleanupTask {

	@Autowired
	private ADPasswordService passwordService;

	@Autowired
	private OS2faktorConfiguration configuration;

	// nightly
	@Scheduled(cron = "0 #{new java.util.Random().nextInt(55)} 4 * * ?")
	public void cleanup() {
		if (configuration.getScheduled().isEnabled()) {
			log.debug("Cleanup of cached AD passwords started");

			passwordService.cleanupCache();
		}
	}
}
