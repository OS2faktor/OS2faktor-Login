package dk.digitalidentity.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.service.CprService;
import dk.digitalidentity.config.OS2faktorConfiguration;

@Component
@EnableScheduling
@Slf4j
public class CPRNameSyncTask {

	@Autowired
	private CprService cprService;

	@Autowired
	private OS2faktorConfiguration configuration;

	@Scheduled(cron = "0 #{new java.util.Random().nextInt(60)} #{new java.util.Random().nextInt(4) + 18} * * ?")
	public void processChanges() {
		if (configuration.getScheduled().isEnabled()) {
			log.info("Syncing names and civilstand from cpr service started");

			try {
				cprService.syncNamesAndCivilstandFromCpr();
			}
			catch (Exception ex) {
				log.error("CPR Sync task failed", ex);
			}
		}
	}
}
