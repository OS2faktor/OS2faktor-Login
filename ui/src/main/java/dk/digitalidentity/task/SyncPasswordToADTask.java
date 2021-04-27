package dk.digitalidentity.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.service.ADPasswordService;
import dk.digitalidentity.config.OS2faktorConfiguration;
import lombok.extern.log4j.Log4j;

@Component
@EnableScheduling
@Log4j
public class SyncPasswordToADTask {

	@Autowired
	private OS2faktorConfiguration configuration;

	@Autowired
	private ADPasswordService adPasswordService;

	// Every minute
	@Scheduled(fixedRate = 1000 * 60)
	public void processChanges() {
		if (configuration.getScheduled().isEnabled()) {
			log.debug("Sync password to AD via Websockets");

			adPasswordService.syncPasswordsToAD();
		}
	}
}
