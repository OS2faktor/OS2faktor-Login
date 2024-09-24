package dk.digitalidentity.task;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import dk.digitalidentity.common.dao.model.PasswordChangeQueue;
import dk.digitalidentity.common.dao.model.PasswordSetting;
import dk.digitalidentity.common.service.ADPasswordService;
import dk.digitalidentity.common.service.EmailService;
import dk.digitalidentity.common.service.PasswordChangeQueueService;
import dk.digitalidentity.common.service.PasswordSettingService;
import dk.digitalidentity.config.OS2faktorConfiguration;
import lombok.extern.slf4j.Slf4j;

@Component
@EnableScheduling
@Slf4j
public class MonitorPasswordSyncTask {
	private Map<String, LocalDateTime> lastNotifications = new HashMap<>();
	
	@Autowired
	private OS2faktorConfiguration configuration;

	@Autowired
	private PasswordSettingService passwordSettingService;

	@Autowired
	private ADPasswordService adPasswordService;
	
	@Autowired
	private PasswordChangeQueueService passwordChangeQueueService;
	
	@Autowired
	private EmailService emailService;

	// Every 5 minutes
	@Scheduled(fixedRate = 1000 * 60 * 5)
	public void processChanges() {
		if (!configuration.getScheduled().isEnabled()) {
			return;
		}
		
		List<PasswordSetting> allSettings = passwordSettingService.getAllSettings();
		for (PasswordSetting setting : allSettings) {

			// if we have disabled monitoring on this domain directly in the database, we will not attempt to detect missing
			// connections. This makes sense for trial setups and stuff that is not quite ready yet...
			if (!setting.getDomain().isMonitored()) {
				continue;
			}

			if (!setting.getDomain().isStandalone()) {
				LocalDateTime lastNotification = lastNotifications.get(setting.getDomain().getName());
				
				// if we send out a notification within the last 4 hours, we do not do it again
				if (lastNotification != null && lastNotification.isAfter(LocalDateTime.now().minusHours(4))) {
					return;
				}
				
				boolean noConnections = false;
				boolean pendingChanges = false;

				if (!adPasswordService.monitorConnection(setting.getDomain().getName())) {
					noConnections = true;
				}

				PasswordChangeQueue oldest = passwordChangeQueueService.getOldestUnsynchronizedByDomain(setting.getDomain().getName());
				if (oldest != null) {
					// if the oldest record has a timestamp that is more than 30 minutes older than now, we have an issue
					if (oldest.getTts().isBefore(LocalDateTime.now().minusMinutes(30))) {
						log.error("Unsynchronized password change - will result in alarm");
						pendingChanges = true;
					}
				}

				if ((noConnections || pendingChanges) && setting.isMonitoringEnabled() && StringUtils.hasLength(setting.getMonitoringEmail())) {
					log.info("Sending notification to " + setting.getMonitoringEmail() + " about password replication issues");

					// remember last notify, so we do not spam
					lastNotification = LocalDateTime.now();
					lastNotifications.put(setting.getDomain().getName(), lastNotification);

					emailService.sendMessage(setting.getMonitoringEmail(), "Fejl i password replikering", "Det er ikke muligt at replikere kodeord til AD fra OS2faktor for '" + setting.getDomain().getName() + "'. En eller flere password replikeringsagenter er gået ned.", null);
				}
			}
		}
	}
}
