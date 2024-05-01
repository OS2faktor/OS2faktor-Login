package dk.digitalidentity.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.config.OS2faktorConfiguration;

@Component
@EnableScheduling
@Slf4j
public class LogCleanupTask {

	@Autowired
	private AuditLogger auditLogger;

	@Autowired
	private OS2faktorConfiguration configuration;

	// nightly - run it multiple times to ensure we delete enough data (LIMIT 25000 on each run)
	@Scheduled(cron = "#{new java.util.Random().nextInt(59)} #{new java.util.Random().nextInt(59)} 2,3,4 * * ?")
	public void processChanges() {
		if (configuration.getScheduled().isEnabled()) {
			log.info("Cleanup of old logs started");

			auditLogger.cleanupLogs();
			auditLogger.cleanupTraceLogs();
			auditLogger.cleanupLoginLogs();
			auditLogger.deleteUnreferencedAuditlogDetails();
			
			log.info("Cleanup of old logs completed");
		}
	}
}
