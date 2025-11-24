package dk.digitalidentity.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.service.ADPasswordService;
import dk.digitalidentity.config.OS2faktorConfiguration;

@Slf4j
@Component
@EnableScheduling
public class SyncQueueCleanupTask {

	@Autowired
	private OS2faktorConfiguration configuration;

	@Autowired
	private ADPasswordService adPasswordService;

	// Nightly
	@Scheduled(cron = "0 #{new java.util.Random().nextInt(55)} 2 * * ?")
	public void processChanges() {
		if (configuration.getScheduled().isEnabled()) {
			log.info("Deleting synchronized passwords from the queue started");

			adPasswordService.syncQueueCleanupTask();
			
			log.info("Deleting synchronized passwords from the queue ended");
		}
	}
}
