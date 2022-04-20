package dk.digitalidentity.task;

import dk.digitalidentity.common.dao.model.TemporaryClientSessionKey;
import dk.digitalidentity.common.service.TemporaryClientSessionKeyService;
import dk.digitalidentity.config.OS2faktorConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@EnableScheduling
@Slf4j
public class DeleteTemporarySessionKeyTask {

    @Autowired
    private TemporaryClientSessionKeyService tempSessionKeyService;
    
    @Autowired
    private OS2faktorConfiguration configuration;

    // Every night at 2(ish)
    @Scheduled(cron = "0 #{new java.util.Random().nextInt(55)} 2 * * *")
    public void processChanges() {
    	if (configuration.getScheduled().isEnabled()) {
			log.info("Deleting all Temporary SessionKeys that are older than five minutes");

			try {
				List<TemporaryClientSessionKey> allOlderThanFiveMin = tempSessionKeyService.getAllWithTtsBefore(LocalDateTime.now().minusMinutes(5));
				tempSessionKeyService.deleteMultiple(allOlderThanFiveMin);
			}
			catch (Exception ex) {
				log.error("Deletion of SessionKeys from database failed", ex);
			}
    	}
    }
}
