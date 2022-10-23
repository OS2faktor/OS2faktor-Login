package dk.digitalidentity.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.service.mfa.MFAService;
import dk.digitalidentity.config.OS2faktorConfiguration;

@Component
@EnableScheduling
public class SyncCachedMfaClients {

	@Autowired
	private OS2faktorConfiguration configuration;
	
	@Autowired
	private MFAService mfaService;

	// nightly
	@Scheduled(cron = "${cron.mfa.db.sync:0 #{new java.util.Random().nextInt(55)} 1 * * ?}")
	public void processChanges() {
		if (configuration.getScheduled().isEnabled()) {
			mfaService.synchronizeCachedMfaClients();
		}
	}
}
