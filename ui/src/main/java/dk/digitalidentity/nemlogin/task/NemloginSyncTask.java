package dk.digitalidentity.nemlogin.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.nemlogin.service.NemLoginService;

@Component
@EnableScheduling
public class NemloginSyncTask {
	
	@Autowired
	private NemLoginService nemLoginService;
	
	@Autowired
	private OS2faktorConfiguration configuration;
	
	@Autowired
	private CommonConfiguration commonConfiguration;
	
	// every two minutes during "daytime"
	@Scheduled(cron = "#{new java.util.Random().nextInt(60)} 0/2 6-23 * * ?")
	public void sync() {
		if (configuration.getScheduled().isEnabled() && commonConfiguration.getNemLoginApi().isEnabled()) {
			nemLoginService.sync();
		}
	}
	
	// nightly - makes sure all users in MitID Erhverv, controlled by us, is suspended if it was missed somehow
	@Scheduled(cron = "${cron.mitid.cleanup:#{new java.util.Random().nextInt(60)} #{new java.util.Random().nextInt(60)} 22 * * ?}")
	public void cleanupMitIDErhverv() {
		if (configuration.getScheduled().isEnabled() && commonConfiguration.getNemLoginApi().isEnabled()) {
			nemLoginService.cleanupMitIDErhverv();
		}
	}
	
	// nightly - makes sure all users in MitID Erhverv, controlled by us, is deleted 3 months after suspend
	@Scheduled(cron = "${cron.mitid.delete:#{new java.util.Random().nextInt(60)} #{new java.util.Random().nextInt(60)} 23 * * ?}")
	public void deleteOldMitIDErhverv() {
		if (configuration.getScheduled().isEnabled() && commonConfiguration.getNemLoginApi().isEnabled()) {
			nemLoginService.deleteMitIDErhverv();
		}
	}
}
