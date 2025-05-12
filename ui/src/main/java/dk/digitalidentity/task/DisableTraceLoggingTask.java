package dk.digitalidentity.task;

import dk.digitalidentity.common.dao.model.enums.SettingKey;
import dk.digitalidentity.common.service.SettingService;
import dk.digitalidentity.config.OS2faktorConfiguration;
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
	private OS2faktorConfiguration configuration;

    @Autowired
    private SettingService settingService;

    // Runs once at midnight
    @Scheduled(cron = "0 0 0 * * *")
    public void disableTraceLogging() {
    	if (configuration.getScheduled().isEnabled()) {
	        log.info("Running task: disableTraceLogging");
	
	        settingService.setBoolean(SettingKey.TRACE_LOGGING, false);
	
	        log.info("Finished task: disableTraceLogging");
    	}
    }
}
