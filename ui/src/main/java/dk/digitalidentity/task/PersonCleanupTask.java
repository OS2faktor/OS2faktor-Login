package dk.digitalidentity.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.config.OS2faktorConfiguration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@EnableScheduling
public class PersonCleanupTask {

	@Autowired
	private OS2faktorConfiguration configuration;
	
	@Autowired
	private PersonService personService;

	// every Thursday evening
	@Scheduled(cron = "0 #{new java.util.Random().nextInt(60)} 21 * * THU")
	public void personCleanupTask() {
		if (configuration.getScheduled().isEnabled()) {
			log.info("Cleanup of persons started");

			personService.cleanUp();
			
			log.info("Cleanup of persons completed");
		}
	}
}
