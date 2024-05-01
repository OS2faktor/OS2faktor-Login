package dk.digitalidentity.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.service.serviceprovider.ServiceProviderFactory;
import lombok.extern.slf4j.Slf4j;

@Component
@EnableScheduling
@Slf4j
public class UpdateServiceProvidersTask {

    @Autowired
    private ServiceProviderFactory serviceProviderFactory;

    @Scheduled(cron = "0 0/5 * * * *")
    public void processChanges() {
        try {
            serviceProviderFactory.loadSQLServiceProviders();
        }
        catch (Exception ex) {
            log.error("Update of ServiceProviders from database task failed", ex);
        }
    }
}
