package dk.digitalidentity.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.service.ExpiredTokenDeletingOAuth2AuthorizationService;
import lombok.extern.slf4j.Slf4j;

@Component
@EnableScheduling
@Slf4j
public class OAuth2AuthorizationCleanupTask {

	@Autowired
	private ExpiredTokenDeletingOAuth2AuthorizationService authorizationService;

	// Runs once every day between 01:00:00 and 01:55:00
	@Scheduled(cron = "0 #{new java.util.Random().nextInt(55)} 1 * * ?")
	public void cleaupTask() {
		try {
			log.info("Running cleanup OAuth Table task");
			long startTime = System.currentTimeMillis();

			authorizationService.removeExpiredAuthorizations();

			long stopTime = System.currentTimeMillis();
			long elapsedTime = stopTime - startTime;

			log.info("Finish cleanup OAuth Table task " + elapsedTime + "ms");
		}
		catch (Exception ex) {
			log.error("Cleanup oauth2 authorization task failed", ex);
		}
	}
}
