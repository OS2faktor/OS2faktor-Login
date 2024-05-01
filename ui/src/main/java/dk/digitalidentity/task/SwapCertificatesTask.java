package dk.digitalidentity.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.service.KeystoreService;

@Component
@EnableScheduling
public class SwapCertificatesTask {

	@Autowired
	private OS2faktorConfiguration configuration;
	
	@Autowired
	private KeystoreService keystoreService;

    @Scheduled(cron = "0 3/5 * * * *")
    public void swapCertificates() {
		if (configuration.getScheduled().isEnabled() && configuration.getCertManagerApi().isEnabled()) {
	    	keystoreService.performCertificateSwap();
		}
    }
}
