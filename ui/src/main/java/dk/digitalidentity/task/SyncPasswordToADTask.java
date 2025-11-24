package dk.digitalidentity.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.service.ADPasswordService;
import dk.digitalidentity.config.OS2faktorConfiguration;

@Slf4j
@Component
@EnableScheduling
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
