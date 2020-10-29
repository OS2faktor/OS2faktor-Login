package dk.digitalidentity.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.service.CprService;
import lombok.extern.log4j.Log4j;

@Component
@EnableScheduling
@Log4j
public class CPRNameSyncTask {

	@Autowired
	private CprService cprService;

	@Autowired
	private OS2faktorConfiguration configuration;

	@Scheduled(cron = "0 #{new java.util.Random().nextInt(55)} 21 * * ?")
	public void processChanges() {
		if (configuration.getScheduled().isEnabled() && configuration.getScheduled().isCprNameSyncEnabled()) {
			log.info("Syncing names from cpr service started");

			try {
				cprService.syncNamesWithCprTask();
			}
			catch (Exception ex) {
				log.error("CPR Sync task failed", ex);
			}
		}
	}
}
