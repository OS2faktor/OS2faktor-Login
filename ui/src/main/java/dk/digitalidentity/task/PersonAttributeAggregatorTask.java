package dk.digitalidentity.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.service.PersonAttributeService;
import dk.digitalidentity.config.OS2faktorConfiguration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@EnableScheduling
public class PersonAttributeAggregatorTask {

	@Autowired
	private PersonAttributeService personAttributeSetService;

	@Autowired
	private OS2faktorConfiguration configuration;

	// reload once every 15 minutes
	@Scheduled(fixedRate = 15 * 60 * 1000)
	public void aggregateAttributes() {
		if (configuration.getScheduled().isEnabled()) {
			log.debug("Aggregation of Person Attributes started");

			personAttributeSetService.aggregateAttributes();			
		}
	}
}
