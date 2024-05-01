package dk.digitalidentity.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.service.PasswordExpiresService;

@Component
@EnableScheduling
public class PasswordExpiresTask {

	@Autowired
	private OS2faktorConfiguration configuration;

	@Autowired
	private PasswordExpiresService passwordExpiresService;

	// Nightly
	@Scheduled(cron = "0 #{new java.util.Random().nextInt(55)} 3 * * ?")
	public void processChanges() {
		if (!configuration.getScheduled().isEnabled()) {
			return;
		}

		passwordExpiresService.notifyPasswordExpires();
	}
}
