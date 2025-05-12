package dk.digitalidentity.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.dao.model.enums.SettingKey;
import dk.digitalidentity.common.service.SettingService;
import dk.digitalidentity.common.service.mfa.MFAService;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.service.MFAManagementService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@EnableScheduling
public class MfaClientsTask {

	@Autowired
	private OS2faktorConfiguration configuration;
	
	@Autowired
	private MFAService mfaService;
	
	@Autowired
	private SettingService settingService;

	@Autowired
	private MFAManagementService mfaManagementService;

	// nightly
	@Scheduled(cron = "${cron.mfa.db.sync:0 #{new java.util.Random().nextInt(55)} 1 * * ?}")
	public void updateMfaCache() {
		if (configuration.getScheduled().isEnabled()) {
			mfaService.synchronizeCachedMfaClients();
		}
	}
	
	// run AFTER the syncMfaClients task, so we are sure we have fresh data
	@Scheduled(cron = "0 #{new java.util.Random().nextInt(59)} 2 * * ?")
	public void removeTOTPHDevicesTask(){
		if (!configuration.getScheduled().isEnabled()) {
			return; // Don't run if scheduled jobs are not enabled
		}

		if (!settingService.getBoolean(SettingKey.REMOVE_DEVICE_WHEN_PERSON_LOCKED)) {
            return;
        }
		
		log.info("Running task: removeTOTPHDevicesTask");

		mfaManagementService.removeTOTPHDevicesOnLockedPersons();
	}
}
