package dk.digitalidentity.service;

import javax.annotation.Nullable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import dk.digitalidentity.common.dao.model.EmailTemplateChild;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.service.EmailService;
import dk.digitalidentity.common.service.EmailTemplateService;
import dk.digitalidentity.common.service.MessageQueueService;
import dk.digitalidentity.common.service.dto.TransformInlineImageDTO;
import dk.digitalidentity.service.EboksService.SendStatus;

@Service
public class EmailTemplateSenderService {
	
	@Autowired
	private EmailTemplateService emailTemplateService;
	
	@Autowired
	private EmailService emailService;
	
	@Autowired
	private EboksService eboksService;
	
	@Autowired
	private MessageQueueService messageQueueService;
	
	public void send(@Nullable String email, @Nullable String cpr, Person recipient, String subject, String message, EmailTemplateChild child, long delayMinutes) {
		send(email, cpr, recipient, subject, message, child, false, delayMinutes);
	}
	
	public void send(@Nullable String email, @Nullable String cpr, Person recipient, String subject, String message, EmailTemplateChild child, boolean bypassQueue) {
		send(email, cpr, recipient, subject, message, child, bypassQueue, 0);
	}

	public void send(@Nullable String email, @Nullable String cpr, Person recipient, String subject, String message, EmailTemplateChild child, boolean bypassQueue, long delayMinutes) {
		// enable/disable are only for non-system-mails (those with a domain associated)
		if (!child.isEnabled() && child.getDomain() != null) {
			return;
		}

		// non-system-mails are always send as email (those without a domain associated)
		if ((child.isEmail() || child.getDomain() == null) && StringUtils.hasLength(email)) {
			sendEmail(email, subject, message, child, bypassQueue, recipient);
		}
		
		if (child.isEboks()) {
			sendEboks(subject, message, child, bypassQueue, cpr, recipient);
		}		
	}
	
	private void sendEboks(String subject, String message, EmailTemplateChild child, boolean bypassQueue, String cpr, Person recipient) {
		if (bypassQueue) {
			SendStatus status = eboksService.sendMessage(cpr, subject, message, recipient);

			if (status != SendStatus.SEND) {
				messageQueueService.queueEboks(recipient, child.getTitle(), message);
			}
		}
		else {
			messageQueueService.queueEboks(recipient, child.getTitle(), message);
		}
	}
	
	private void sendEmail(String email, String subject, String message, EmailTemplateChild child, boolean bypassQueue, Person recipient) {
		if (bypassQueue) {
			TransformInlineImageDTO inlineImagesDto = emailTemplateService.transformImages(message);
			boolean success = emailService.sendMessage(email, subject, inlineImagesDto.getMessage(), inlineImagesDto.getInlineImages(), recipient);
			
			if (!success) {
				messageQueueService.queueEmail(recipient, child.getTitle(), message);
			}			
		}
		else {
			messageQueueService.queueEmail(recipient, child.getTitle(), message);
		}
	}
}
