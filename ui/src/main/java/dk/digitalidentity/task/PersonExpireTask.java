package dk.digitalidentity.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.config.OS2faktorConfiguration;

@Component
@EnableScheduling
@Slf4j
public class PersonExpireTask {

	@Autowired
	private OS2faktorConfiguration configuration;
	
	@Autowired
	private PersonService personService;

	// run in the morning, midday and evening, to ensure accounts expire
	@Scheduled(cron = "0 #{new java.util.Random().nextInt(55)} 5,12,18 * * ?")
	// @Scheduled(fixedDelay = 60 * 1000)
	public void processChanges() {
		if (configuration.getScheduled().isEnabled()) {
			log.info("Checking for expired Persons started");

			personService.handleExpired();
			
			log.info("Checking for expired Persons completed");
		}
	}
}
