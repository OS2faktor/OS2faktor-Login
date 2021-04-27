package dk.digitalidentity.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.service.ADPasswordService;
import dk.digitalidentity.config.OS2faktorConfiguration;
import lombok.extern.log4j.Log4j;

@Component
@EnableScheduling
@Log4j
public class SyncQueueCleanupTask {

	@Autowired
	private OS2faktorConfiguration configuration;

	@Autowired
	private ADPasswordService adPasswordService;

	// Nightly
	@Scheduled(cron = "0 #{new java.util.Random().nextInt(55)} 2 * * ?")
	public void processChanges() {
		if (configuration.getScheduled().isEnabled()) {
			log.debug("Delete synchronized passwords from the queue");

			adPasswordService.syncQueueCleanupTask();
		}
	}
}
