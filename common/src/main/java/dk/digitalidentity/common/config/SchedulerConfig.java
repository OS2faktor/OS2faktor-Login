package dk.digitalidentity.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

// virtual threads enables SimpleAsyncTaskScheduler, which allows the same task to run multiple times (as a side-effect), and we do NOT want
// that, so this configuration re-enabled the old behavior
@Configuration
public class SchedulerConfig implements SchedulingConfigurer {

	@Bean(destroyMethod = "shutdown")
	public TaskScheduler taskScheduler() {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(1);
		scheduler.setThreadNamePrefix("scheduled-");
		scheduler.setRemoveOnCancelPolicy(true);

		// explicitly do NOT use virtual threads
		scheduler.setVirtualThreads(false);
		scheduler.initialize();

		return scheduler;
	}

	@Override
	public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
		taskRegistrar.setTaskScheduler(taskScheduler());
	}
}
