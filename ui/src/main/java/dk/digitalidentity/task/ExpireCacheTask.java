package dk.digitalidentity.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.service.StatisticsService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@EnableScheduling
public class ExpireCacheTask {

    @Autowired
    private StatisticsService statisticsService;

	@Scheduled(fixedRate = 2 * 60 * 1000)
	public void everyTwoMinutes() {
		statisticsService.cleanupRealtimeValues();
	}

	@Scheduled(fixedRate = 10 * 60 * 1000)
	public void everyTenMinutes() {
		statisticsService.cleanupHourly();
	}

	@Scheduled(cron = "#{new java.util.Random().nextInt(55)} 3 * * * *")
	public void Daily() {
		statisticsService.cleanupDaily();
	}
}
