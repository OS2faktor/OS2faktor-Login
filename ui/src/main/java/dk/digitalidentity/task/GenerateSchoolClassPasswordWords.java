package dk.digitalidentity.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.service.SchoolClassService;
import dk.digitalidentity.config.OS2faktorConfiguration;
import lombok.extern.slf4j.Slf4j;

@Component
@EnableScheduling
@Slf4j
public class GenerateSchoolClassPasswordWords {

	@Autowired
	private OS2faktorConfiguration configuration;

	@Autowired
	private SchoolClassService schoolClassService;
	
    @Scheduled(cron = "0 #{new java.util.Random().nextInt(55)} 3 * * *")
//    @Scheduled(fixedDelay = 60 * 60 * 1000)
	public void processChanges() {
		if (configuration.getScheduled().isEnabled()) {
			log.info("Generating class password lists");

			schoolClassService.generateSchoolClassPasswordWords();
		}
	}
}
