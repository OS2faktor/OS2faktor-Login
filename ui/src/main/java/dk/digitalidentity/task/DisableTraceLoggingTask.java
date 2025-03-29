package dk.digitalidentity.task;

import dk.digitalidentity.common.dao.model.enums.SettingKey;
import dk.digitalidentity.common.service.SettingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableScheduling
public class DisableTraceLoggingTask {

    @Autowired
    SettingService settingService;

    // Runs once at midnight
    @Scheduled(cron = "* * 0 * * *")
    public void disableTraceLogging() {
        log.info("Running task: disableTraceLogging");

        settingService.setBoolean(SettingKey.TRACE_LOGGING, false);

        log.info("Finished task: disableTraceLogging");
    }
}
