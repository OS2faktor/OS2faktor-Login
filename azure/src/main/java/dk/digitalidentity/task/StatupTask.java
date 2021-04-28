package dk.digitalidentity.task;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dk.digitalidentity.config.OS2faktorAzureADSyncConfiguration;
import dk.digitalidentity.service.AzureAdService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class StatupTask {

    @Autowired
    private OS2faktorAzureADSyncConfiguration configuration;

    @Autowired
    private AzureAdService azureAdService;

    @PostConstruct
    public void fullSync() throws Exception {
        if (configuration.getScheduled().isEnabled()) {
            log.info("CoreData sync running (full)");
            azureAdService.fullSync();
            log.info("CoreData sync completed (full)");
        }
    }
}
