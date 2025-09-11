package dk.digitalidentity.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.config.OS2faktorConfiguration;
import lombok.extern.slf4j.Slf4j;

@Component
@EnableScheduling
@Slf4j
public class ResetNSISLevelOnIncompleteActivationsTask {
	
	@Autowired
	private PersonService personService;

	@Autowired
	private OS2faktorConfiguration configuration;

	// Runs once every day between 01:00:00 and 01:55:00 cron = "0 #{new java.util.Random().nextInt(55)} 1 * * ?"
	@Scheduled(cron = "0 #{new java.util.Random().nextInt(55)} 1 * * ?")
	public void resetNSISLevelOnIncompleteActivations(){
		if (!configuration.getScheduled().isEnabled()) {
			return; // Don't run if scheduled jobs are not enabled
		}
		
		log.info("Running task: resetNSISLevelOnIncompleteActivations");
		personService.resetNSISLevelOnIncompleteActivations();
		
		log.info("Running task: resetNSISLevelOnIncompleteActivations ended");
	}
}
