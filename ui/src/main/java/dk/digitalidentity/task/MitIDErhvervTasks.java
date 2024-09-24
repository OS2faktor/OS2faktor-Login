package dk.digitalidentity.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.service.NemloginQueueService;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.nemlogin.service.NemLoginService;
import lombok.extern.slf4j.Slf4j;

@Component
@EnableScheduling
@Slf4j
public class MitIDErhvervTasks {
	
	@Autowired
	private NemLoginService nemLoginService;

	@Autowired
	private OS2faktorConfiguration configuration;
	
	@Autowired
	private CommonConfiguration commonConfiguration;

	@Autowired
	private NemloginQueueService nemloginQueueService;

	// Runs once every day between 21:00:00 and 21:59:00 cron = "0 #{new java.util.Random().nextInt(55)} 1 * * ?"
	@Scheduled(cron = "0 #{new java.util.Random().nextInt(60)} 21 * * ?")
	public void fixMissingCreateSuspendOrders(){
		if (!configuration.getScheduled().isEnabled() || !commonConfiguration.getNemLoginApi().isEnabled()) {
			return; // Don't run if scheduled jobs are not enabled
		}

		log.info("Running task: fixMissingCreateSuspendOrders");
		
		nemLoginService.fixMissingCreateSuspendOrders();

		log.info("Completed task: fixMissingCreateSuspendOrders");
	}
	
	// Runs once every day between 02:00:00 and 05:59:00"
	@Scheduled(cron = "${cron.mitid.sync:0 #{new java.util.Random().nextInt(60)} #{new java.util.Random().nextInt(4) + 2} * * ?}")
	public void syncMitIDErhvervCache(){
		if (!configuration.getScheduled().isEnabled() || !commonConfiguration.getNemLoginApi().isEnabled()) {
			return; // Don't run if scheduled jobs are not enabled
		}

		log.info("Running task: syncMitIDErhvervCache");
		
		nemLoginService.syncMitIDErhvervCache();

		log.info("Completed task: syncMitIDErhvervCache");

		// run this immediately afterwards
		
		log.info("Running task: checkForIncorrectDataEntries");

		nemLoginService.checkForIncorrectDataEntries();

		log.info("Completed task: checkForINcorrectDataEntries");

	}
	
	@Scheduled(cron = "0 #{new java.util.Random().nextInt(60)} 5 * * ?")
	public void cleanupFailedMitIDQeueueEntries() {
		if (configuration.getScheduled().isEnabled()) {
			log.info("Running task: cleanupFailedMitIDQeueueEntries");

			nemloginQueueService.cleanupOldFailedEntries();
			
			log.info("Completed task: cleanupFailedMitIDQeueueEntries");
		}
	}
}
