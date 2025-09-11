package dk.digitalidentity.common.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import dk.digitalidentity.common.dao.MessageQueueDao;
import dk.digitalidentity.common.dao.model.EmailTemplate;
import dk.digitalidentity.common.dao.model.MessageQueue;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.EmailTemplateType;
import dk.digitalidentity.common.service.dto.TransformInlineImageDTO;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class MessageQueueService {

	@Autowired
	private MessageQueueDao messageQueueDao;

	@Autowired
	private EmailService emailService;

	@Autowired
	private EmailTemplateService emailTemplateService;

	@Autowired
	private PersonService personService;
	
	@Autowired
	private MessageQueueService self;
	
	public long countNotApprovedByOperator() {
		return messageQueueDao.countByOperatorApprovedFalse();
	}

	public List<MessageQueue> findTop10ByDeliveryTtsBeforeAndCprNotNull(LocalDateTime tts) {
		return messageQueueDao.findTop10ByDeliveryTtsBeforeAndCprNotNull(tts);
	}

	@Transactional
	public void delete(MessageQueue messageQueue) {
		messageQueueDao.delete(messageQueue);
	}

	public void queueEboks(Person person, String subject, String message) {
		queueEboks(person, subject, message, 0);
	}

	public void queueEboks(Person person, String subject, String message, long delayMinutes) {
		MessageQueue messageQueue = new MessageQueue();
		messageQueue.setCpr(person.getCpr());
		messageQueue.setMessage(message);
		messageQueue.setSubject(subject);
		messageQueue.setDeliveryTts(getDeliveryTts(delayMinutes));
		messageQueue.setPersonId(person.getId());

		messageQueueDao.save(messageQueue);
	}

	public void queueEmail(Person person, String subject, String message) {
		queueEmail(person, subject, message, 0);
	}

	public void queueEmail(Person person, String subject, String message, long delayMinutes) {
		MessageQueue messageQueue = new MessageQueue();
		messageQueue.setEmail(person.getEmail());
		messageQueue.setMessage(message);
		messageQueue.setSubject(subject);
		messageQueue.setDeliveryTts(getDeliveryTts(delayMinutes));
		messageQueue.setPersonId(person.getId());

		messageQueueDao.save(messageQueue);
	}

	public void dequeue(String cpr, String email, EmailTemplateType emailTemplateType) {
		EmailTemplate emailTemplate = emailTemplateService.findByTemplateType(emailTemplateType);
		
		// get the subjects on all active templates of this type - it is not perfect, but we will use the subject
		// as a match-key for dequeuing
		Set<String> subjects = emailTemplate.getChildren().stream()
				.filter(c -> c.isEnabled())
				.map(c -> c.getTitle())
				.collect(Collectors.toSet());
		
		if (subjects.size() == 0) {
			return;
		}
		
		if (StringUtils.hasLength(cpr)) {
			List<MessageQueue> messages = messageQueueDao.findByCpr(cpr);
			
			for (MessageQueue message : messages) {
				if (subjects.stream().anyMatch(s -> Objects.equals(s, message.getSubject()))) {
					messageQueueDao.delete(message);
				}
			}
		}
		
		if (StringUtils.hasLength(email)) {
			List<MessageQueue> messages = messageQueueDao.findByEmail(email);

			for (MessageQueue message : messages) {
				if (subjects.stream().anyMatch(s -> Objects.equals(s, message.getSubject()))) {
					messageQueueDao.delete(message);
				}
			}
		}
	}

	public void sendPendingEmails() {
		List<MessageQueue> emails = messageQueueDao.findTop10ByDeliveryTtsBeforeAndEmailNotNull(LocalDateTime.now());

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
				self.deleteFromQueue(email);
			}
			else {
				log.error("Failed to send MessageQueue message with id " + email.getId());
			}
		}
	}

	@Transactional // this is OK, as we need a transaction to delete
	public void deleteFromQueue(MessageQueue email) {
		messageQueueDao.delete(email);		
	}

	private LocalDateTime getDeliveryTts(long delay) {
		return LocalDateTime.now().plusMinutes(delay);
	}
}
