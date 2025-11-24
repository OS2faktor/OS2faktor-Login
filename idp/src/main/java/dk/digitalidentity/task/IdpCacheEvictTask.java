package dk.digitalidentity.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.service.WSFederationService;

@Component
@EnableScheduling
public class IdpCacheEvictTask {

    @Autowired
    private WSFederationService wsFederationService;

	@Scheduled(fixedRate = 5 * 60 * 1000)
	public void everyFiveMinutes() {
		wsFederationService.cacheEvict();
	}
}
