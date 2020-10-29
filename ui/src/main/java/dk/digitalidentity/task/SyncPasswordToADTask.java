package dk.digitalidentity.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.dao.model.PasswordSetting;
import dk.digitalidentity.common.service.PasswordSettingService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.config.OS2faktorConfiguration;
import lombok.extern.log4j.Log4j;

@Component
@EnableScheduling
@Log4j
public class SyncPasswordToADTask {

	@Autowired
	private OS2faktorConfiguration configuration;

	@Autowired
	private PasswordSettingService passwordSettingService;

	@Autowired
	private PersonService personService;

	// Every minute
	@Scheduled(fixedRate = 1000 * 60)
	public void processChanges() {
		PasswordSetting passwordSettings = passwordSettingService.getSettings();

		if (configuration.getScheduled().isEnabled() && passwordSettings.isReplicateToAdEnabled()) {
			log.debug("Sync password to AD via Websockets");

			personService.syncPasswordsToAD();
		}
	}
}
