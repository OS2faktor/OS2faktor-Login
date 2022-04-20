package dk.digitalidentity.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.config.OS2faktorConfiguration;
import lombok.extern.slf4j.Slf4j;

@Component
@EnableScheduling
@Slf4j
public class HMacCleanupTask {

	@Autowired
	private AuditLogger auditLogger;

	@Autowired
	private OS2faktorConfiguration configuration;

	// TODO: remove this code once everyone is updated
	// yearly (so basically on restart)
	@Scheduled(fixedDelay = 365 * 24 * 60 * 60 * 1000)
	public void processChanges() {
		if (configuration.getScheduled().isEnabled()) {
			log.debug("Cleanup of null HMACs started");

			auditLogger.cleanupHMAC();
		}
	}
}