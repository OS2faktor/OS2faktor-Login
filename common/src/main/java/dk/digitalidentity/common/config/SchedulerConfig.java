package dk.digitalidentity.common.config;

import java.util.concurrent.Executors;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

// virtual threads enables SimpleAsyncTaskScheduler, which allows the same task to run multiple times (as a side-effect), and we do NOT want
// that, so this configuration re-enabled the old behavior
@Configuration
public class SchedulerConfig implements SchedulingConfigurer {

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setScheduler(Executors.newSingleThreadScheduledExecutor());
    }
}
