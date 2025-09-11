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
public class CleanupOldStudentPasswordTask {
	
	@Autowired
	private OS2faktorConfiguration configuration;
	
	@Autowired
	private PersonService personService;
	
	// nightly - finds all older students with a studentPassword set, and clears it
	// @Scheduled(fixedRate = 60 * 1000)
	@Scheduled(cron = "${cron.mfa.db.sync:0 #{new java.util.Random().nextInt(55)} 3 * * ?}")
	public void processChanges() {
		if (configuration.getScheduled().isEnabled()) {
			log.info("Running cleanupOldStudentsPasswordTask");
			personService.cleanupOldStudentsPasswords();
			log.info("Running cleanupOldStudentsPasswordTask ended");
		}
	}
}
