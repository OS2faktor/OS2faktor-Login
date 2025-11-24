package dk.digitalidentity.task;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import dk.digitalidentity.common.dao.model.MessageQueue;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.service.EmailService;
import dk.digitalidentity.common.service.EmailTemplateService;
import dk.digitalidentity.common.service.MessageQueueService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.dto.TransformInlineImageDTO;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.service.EboksService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@EnableScheduling
public class SendPendingMessagesTask {

	@Autowired
	private OS2faktorConfiguration configuration;

	@Autowired
	private MessageQueueService messageQueueService;

	@Autowired
	private EboksService eboksService;
	
	@Autowired
	private PersonService personService;
	
	@Autowired
	private EmailService emailService;
	
	@Autowired
	private EmailTemplateService emailTemplateService;

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
			sendPendingEmails();
			eboksService.sendPendingEboksMessages();
		}
	}
	
	private void sendPendingEmails() {
		List<MessageQueue> emails = messageQueueService.find10Pending();

		if (emails.size() > 0) {
			log.info("Found " + emails.size() + " pending emails");
		}

		for (MessageQueue email : emails) {
			boolean success = false;
			
			if (StringUtils.hasLength(email.getEmail())) {
				Person person = personService.getById(email.getPersonId(), p -> {
					p.getDomain().getName();
				});

				if (person != null) {
					TransformInlineImageDTO inlineImagesDto = emailTemplateService.transformImages(email.getMessage());
					success = emailService.sendMessage(email.getEmail(), email.getSubject(), inlineImagesDto.getMessage(), inlineImagesDto.getInlineImages(), person);
				}
				else {
					log.warn("Could not find person with ID " + email.getPersonId() + " when sending mail - skipping");
					success = true;
				}
			}
			else {
				log.error("Cannot send message with id '" + email.getId() + "' due to no email");
			}

			if (success) {
				messageQueueService.deleteFromQueue(email);
			}
			else {
				log.error("Failed to send MessageQueue message with id " + email.getId());
			}
		}
	}

}
