package dk.digitalidentity.service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.MessageQueue;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.MessageQueueService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.service.eboks.dto.EboksMessage;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class EboksService {

	@Autowired
	private OS2faktorConfiguration configuration;
	
	@Autowired
	private CommonConfiguration common;
	
	@Autowired
	private TemplateEngine templateEngine;

	@Autowired
	private MessageQueueService messageQueueService;

	@Autowired
	private AuditLogger auditLogger;

	@Autowired
	private PersonService personService;

	public enum SendStatus { SEND, TECHNICAL_ERROR, INVALID_RECIPIENT, RECIPIENT_NOT_SUBSCRIBED, DISABLED };
	
	public SendStatus sendMessage(String cpr, String subject, String message, Person person) {
		if (!configuration.getEboks().isEnabled()) {
			log.warn("e-boks server is not configured - not sending digital post!");
			return SendStatus.DISABLED;
		}

		if (!validCpr(cpr)) {
			log.warn("Not a valid cpr: " + cpr);
			return SendStatus.INVALID_RECIPIENT;
		}

		if (!PersonService.isOver18(cpr)) {
			log.info("Not sending e-boks to person with cpr = " + PersonService.maskCpr(cpr) + ". Age is under 18");
			return SendStatus.INVALID_RECIPIENT;
		}

		RestTemplate restTemplate = new RestTemplate();

		log.info("Sending e-boks message: '" + subject + "' to " + PersonService.maskCpr(cpr));

		if (!StringUtils.hasLength(configuration.getEboks().getSenderName())) {
			log.error("Bad configuration: missing senderName");
			return SendStatus.TECHNICAL_ERROR;
		}

		try {
			EboksMessage eBoks = new EboksMessage();
			eBoks.setCpr(cpr);
			eBoks.setCvr(StringUtils.hasLength(configuration.getEboks().getOverrideCvr()) ? configuration.getEboks().getOverrideCvr() : common.getCustomer().getCvr());
			eBoks.setSubject(subject);
			eBoks.setContent(Base64.getEncoder().encodeToString(generatePDF(subject, message)));
			eBoks.setMunicipalityName(configuration.getEboks().getSenderName());

			HttpHeaders headers = new HttpHeaders();
			headers.add("Content-Type", "application/json");
			HttpEntity<EboksMessage> request = new HttpEntity<EboksMessage>(eBoks, headers);

			ResponseEntity<String> response = restTemplate.postForEntity(configuration.getEboks().getUrl(), request, String.class);
			
			if (response.getStatusCode().value() != 200) {
				if (response.getStatusCode().value() == 409) {
					log.warn("Failed to send e-boks message to: " + PersonService.maskCpr(cpr) + " - person not subscribed");
					return SendStatus.RECIPIENT_NOT_SUBSCRIBED;
				}
				else {
					log.error("Failed to send e-boks message to: " + PersonService.maskCpr(cpr) + ". HTTP: " + response.getStatusCode().value());
					return SendStatus.TECHNICAL_ERROR;
				}
			}

			auditLogger.sentEBoks(person, subject);
		}
		catch (HttpStatusCodeException ex) {
			if (ex.getStatusCode().value() == 409) {
				log.warn("e-boks not subscribed: " + PersonService.maskCpr(cpr));
				return SendStatus.RECIPIENT_NOT_SUBSCRIBED;
			}
			else {
				log.error("Failed to send e-boks message to: " + PersonService.maskCpr(cpr), ex);
				return SendStatus.TECHNICAL_ERROR;
			}
		}
		
		return SendStatus.SEND;
	}

	@Transactional
	public void sendPendingEboksMessages() {
		List<MessageQueue> messages = messageQueueService.findTop10ByDeliveryTtsBeforeAndCprNotNull(LocalDateTime.now());

		if (messages.size() > 0) {
			log.info("Found " + messages.size() + " pending eboks messages");
		}

		for (MessageQueue message : messages) {
			Person recipient = personService.getById(message.getPersonId());
			if (recipient == null) {
				log.error("Unable to find recipient with ID: " + message.getPersonId());
				messageQueueService.delete(message);
				continue;
			}
			
			SendStatus status;
			if (StringUtils.hasLength(message.getCpr())) {
				status = sendMessage(message.getCpr(), message.getSubject(), message.getMessage(), recipient);
			}
			else {
				log.error("Cannot send message with id '" + message.getId() + "' due to no cpr");
				status = SendStatus.INVALID_RECIPIENT;
			}

			switch (status) {
				case TECHNICAL_ERROR:
					// retry later
					continue;

				case SEND:
				case DISABLED:
				case INVALID_RECIPIENT:
				case RECIPIENT_NOT_SUBSCRIBED:
					// just break so we end up deleting the queued message
					break;
			}
			
			messageQueueService.delete(message);
		}
	}
	
	private byte[] generatePDF(String subject, String message) {
		// fix escape characters (with space after, to ensure it does not affect actually escape-stuff)
		message = message.replace("& ", "&amp; ");
		
		Context ctx = new Context();
		ctx.setVariable("subject", subject);
		ctx.setVariable("message", message);

		String htmlContent = templateEngine.process("pdf/template", ctx);

		// Create PDF document and return as byte[]
		try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
			ITextRenderer renderer = new ITextRenderer();
			renderer.setDocumentFromString(htmlContent);
			renderer.layout();
			renderer.createPDF(outputStream);

			return outputStream.toByteArray();
		}
		catch (Exception ex) {
			log.error("Failed to generate pdf", ex);
			return null;
		}
	}
	
	// copied from NemLoginService
	private boolean validCpr(String cpr) {
		if (cpr == null || cpr.length() != 10) {
			return false;
		}
		
		for (char c : cpr.toCharArray()) {
			if (!Character.isDigit(c)) {
				return false;
			}
		}
		
		int days = Integer.parseInt(cpr.substring(0, 2));
		int month = Integer.parseInt(cpr.substring(2, 4));

		if (days < 1 || days > 31) {
			return false;
		}

		if (month < 1 || month > 12) {
			return false;
		}

		return true;
	}
}