package dk.digitalidentity.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.config.OS2faktorConfiguration;

@Slf4j
@Component
@EnableScheduling
public class DailyPasswordChangeCounterReset {

	@Autowired
	private OS2faktorConfiguration configuration;
	
	@Autowired
	private PersonService personService;

	// nightly
	@Scheduled(cron = "0 #{new java.util.Random().nextInt(55)} 1 * * ?")
	public void processChanges() {
		if (configuration.getScheduled().isEnabled()) {
			log.info("Daily reset of password change counter started");

			personService.resetDailyPasswordCounter();
			
			log.info("Daily reset of password change counter done.");
		}
	}
}
