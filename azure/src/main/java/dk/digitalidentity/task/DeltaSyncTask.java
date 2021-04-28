package dk.digitalidentity.task;

import dk.digitalidentity.config.OS2faktorAzureADSyncConfiguration;
import dk.digitalidentity.service.AzureAdService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableScheduling
public class DeltaSyncTask {

    @Autowired
    private OS2faktorAzureADSyncConfiguration configuration;

    @Autowired
    private AzureAdService azureAdService;

    @Scheduled(cron = "0 0/5 5-21 * * ?")
    public void syncChanges() throws Exception {
        if (configuration.getScheduled().isEnabled()) {
            log.info("CoreData sync running (delta)");
            azureAdService.deltaSync();
            log.info("CoreData sync completed (delta)");
        }
    }
}
