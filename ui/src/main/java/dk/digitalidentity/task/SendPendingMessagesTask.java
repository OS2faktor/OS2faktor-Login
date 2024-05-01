package dk.digitalidentity.task;

import dk.digitalidentity.common.service.MessageQueueService;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.service.EboksService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@Slf4j
public class SendPendingMessagesTask {

	@Autowired
	private OS2faktorConfiguration configuration;

	@Autowired
	private MessageQueueService messageQueueService;

	@Autowired
	private EboksService eboksService;

	// every minute during "daytime"
	@Scheduled(cron = "#{new java.util.Random().nextInt(55)} * 5-23 * * ?")
	public void processChanges() {
		if (configuration.getScheduled().isEnabled()) {
			log.debug("Send pending messages");

			if (messageQueueService.countNotApprovedByOperator() > 100) {
				log.error("There are more than 100 messages in the queue... needs manual approval from operator before sending anything");
				return;
			}
			
			// eboksService is only in ui, so we are sending messages via two different methods
			messageQueueService.sendPendingEmails();
			eboksService.sendPendingEboksMessages();
		}
	}
}
