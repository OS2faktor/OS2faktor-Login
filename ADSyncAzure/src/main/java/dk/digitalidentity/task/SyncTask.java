package dk.digitalidentity.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.config.OS2faktorAzureADSyncConfiguration;
import dk.digitalidentity.service.AzureAdService;
import dk.digitalidentity.service.KombitRoleAdService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@EnableScheduling
public class SyncTask {
	private long counter = 0;

	@Autowired
	private OS2faktorAzureADSyncConfiguration configuration;

	@Autowired
	private AzureAdService azureAdService;

	@Autowired
	private KombitRoleAdService kombitRoleAdService;

	@Scheduled(cron = "0 0/5 5-21 * * ?")
	public void sync() throws Exception {
		if (configuration.getScheduled().isEnabled()) {
			// perform a full sync every 4 hours, and then delta every 5 minutes
			boolean fullSync = (counter % (4 * 12) == 0);
			counter++;
	
			if (fullSync) {
				log.info("CoreData sync running (full)");
				azureAdService.fullSync();
	
				if (configuration.getScheduled().isKombitRolesEnabled()) {
					log.info("KombitRole sync running (full)");
					kombitRoleAdService.fullSync();
				}
	
				log.info("CoreData sync completed (full)");
			}
			else {
				azureAdService.deltaSync();
	
				if (configuration.getScheduled().isKombitRolesEnabled()) {
					kombitRoleAdService.deltaSync();
				}
				
				log.info("CoreData sync completed (delta)");
			}
		}
	}
}
