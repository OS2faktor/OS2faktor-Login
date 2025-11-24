package dk.digitalidentity.common.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.service.CprService;
import dk.digitalidentity.common.service.KnownNetworkService;
import dk.digitalidentity.common.service.PasswordSettingService;
import dk.digitalidentity.common.service.SettingService;
import dk.digitalidentity.common.service.TermsAndConditionsService;

@Component
@EnableScheduling
public class CommonCacheEvictTask {

	@Autowired
	private SettingService settingService;
	
	@Autowired
	private PasswordSettingService passwordSettingService;
	
	@Autowired
	private TermsAndConditionsService termsAndConditionsService;
	
	@Autowired
	private KnownNetworkService knownNetworkService;

	@Autowired
	private CprService cprService;

	@Scheduled(fixedRate = 5 * 60 * 1000)
	public void everyFiveMinutes() {
		settingService.cleanupCache();
		passwordSettingService.cleanPasswordSettingsCache();
		knownNetworkService.cacheEvict();
	}
	
	@Scheduled(fixedRate = 30 * 60 * 1000)
	public void everyThirtyMinutes() {
		termsAndConditionsService.cleanupCache();
		cprService.cleanChildrenCache();
	}
}
