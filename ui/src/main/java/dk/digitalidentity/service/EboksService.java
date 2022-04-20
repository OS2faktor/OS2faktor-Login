package dk.digitalidentity.service;

import java.io.ByteArrayOutputStream;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.service.eboks.dto.EboksMessage;
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

	public boolean sendMessage(String cpr, String subject, String message) {
		if (!configuration.getEboks().isEnabled()) {
			log.warn("e-boks server is not configured - not sending digital post!");
			return false;
		}

		RestTemplate restTemplate = new RestTemplate();

		log.info("Sending e-boks message: '" + subject + "' to " + PersonService.maskCpr(cpr));

		String resourceUrl = configuration.getEboks().getUrl();
		if (!resourceUrl.endsWith("/")) {
			resourceUrl += "/";
		}
		resourceUrl += "api/remotePrint/SendLetterToCpr";

		try {
			EboksMessage eBoks = new EboksMessage();
			eBoks.setContentTypeId(configuration.getEboks().getMaterialeId());
			eBoks.setCpr(cpr);
			eBoks.setCvr(common.getCustomer().getCvr());
			eBoks.setSenderId(configuration.getEboks().getSenderId());
			eBoks.setSubject(subject);
			eBoks.setPdfFileBase64(Base64.getEncoder().encodeToString(generatePDF(subject, message)));

			HttpHeaders headers = new HttpHeaders();
			headers.add("Content-Type", "application/json");
			HttpEntity<EboksMessage> request = new HttpEntity<EboksMessage>(eBoks, headers);

			ResponseEntity<String> response = restTemplate.postForEntity(resourceUrl, request, String.class);
			
			if (response.getStatusCodeValue() != 200) {
				log.error("Failed to send e-boks message to: " + PersonService.maskCpr(cpr) + ". HTTP: " + response.getStatusCodeValue());
				return false;
			}
		}
		catch (RestClientException ex) {
			log.error("Failed to send e-boks message to: " + PersonService.maskCpr(cpr), ex);
			return false;
		}
		
		return true;
	}
	
	private byte[] generatePDF(String subject, String message) {
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
}