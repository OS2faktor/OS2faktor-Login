package dk.digitalidentity.task;

import dk.digitalidentity.common.service.PersonService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.config.OS2faktorConfiguration;

@Component
@EnableScheduling
@Slf4j
public class UnlockAccountTask {

    @Autowired
    private PersonService personService;
    
    @Autowired
    private OS2faktorConfiguration configuration;

    // check every minute
    @Scheduled(fixedRate = 60 * 1000)
    public void processChanges() {
    	if (configuration.getScheduled().isEnabled()) {
	        log.debug("Unlocking of locked accounts started");
	        personService.unlockAccounts();
    	}
    }
}
