package dk.digitalidentity.task;

import dk.digitalidentity.common.service.CoreDataLogService;
import dk.digitalidentity.config.OS2faktorConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@Slf4j
public class CoreDataSyncMonitoringTask {

    @Autowired
    private CoreDataLogService coreDataLogService;

    @Autowired
    private OS2faktorConfiguration configuration;

    @Scheduled(cron = "0 #{new java.util.Random().nextInt(55)} 14 * * WED")
    public void monitorSync() {
        if (configuration.getScheduled().isEnabled()) {
            log.info("Monitoring of CoreDataAPI calls started");

            try {
                coreDataLogService.monitorCoreDataSync();
            }
            catch (Exception ex) {
                log.error("CoreData sync task failed", ex);
            }
        }
    }
}
