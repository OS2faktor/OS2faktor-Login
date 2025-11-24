package dk.digitalidentity.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.controller.MetadataController;
import dk.digitalidentity.service.CredentialService;
import dk.digitalidentity.service.KeystoreService;

@Component
@EnableScheduling
public class KeystoreReloadTask {
	
	@Autowired
	private KeystoreService keystoreService;
	
	@Autowired
	private MetadataController metadataController;
	
	@Autowired
	private CredentialService credentialService;

	@Scheduled(cron = "0 0/5 * * * ?")
	public void reloadKeystores() {
		if (keystoreService.loadKeystores()) {
			metadataController.evictCache();
			credentialService.evictCache();
		}
	}
}
