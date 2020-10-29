package dk.digitalidentity.task;

import dk.digitalidentity.common.dao.model.PasswordSetting;
import dk.digitalidentity.common.service.PasswordSettingService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.config.OS2faktorConfiguration;
import lombok.extern.log4j.Log4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@Log4j
public class SyncQueueCleanupTask {

	@Autowired
	private OS2faktorConfiguration configuration;

	@Autowired
	private PasswordSettingService passwordSettingService;

	@Autowired
	private PersonService personService;

	// Nightly
	@Scheduled(cron = "0 #{new java.util.Random().nextInt(55)} 2 * * ?")
	public void processChanges() {
		PasswordSetting passwordSettings = passwordSettingService.getSettings();

		if (configuration.getScheduled().isEnabled() && passwordSettings.isReplicateToAdEnabled()) {
			log.debug("Delete synchronized passwords from the queue");

			personService.syncQueueCleanupTask();
		}
	}
}
