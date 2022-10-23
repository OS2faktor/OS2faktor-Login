package dk.digitalidentity.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.model.EmailTemplate;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.EmailTemplateType;
import dk.digitalidentity.common.service.EmailService;
import dk.digitalidentity.common.service.EmailTemplateService;
import dk.digitalidentity.common.service.dto.InlineImageDTO;
import dk.digitalidentity.service.dto.TransformInlineImageDTO;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class EmailTemplateSenderService {

	@Autowired
	private EmailTemplateService emailTemplateService;
	
	@Autowired
	private EmailService emailService;
	
	@Autowired
	private EboksService eboksService;
	
	public void send(EmailTemplateType type, Person recipient) {
		EmailTemplate template = emailTemplateService.findByTemplateType(type);
		if (!template.isEnabled()) {
			return;
		}
		
		if (template.isEboks()) {
			String message = template.getMessage().replace(EmailTemplateService.RECIPIENT_PLACEHOLDER, recipient.getName());
			boolean success = eboksService.sendMessage(recipient.getCpr(), template.getTitle(), message);
			if (!success) {
				log.warn("Tried to send eboks message from email template of type " + type.toString() + " to person with uuid " + recipient.getUuid() + " but the eboksService.sendMessage failed.");
			}
		}
		
		if (template.isEmail()) {
			if (recipient.getEmail() == null || recipient.getEmail().isEmpty()) {
				log.warn("Tried to send email template of type " + type.toString() + " to person with uuid " + recipient.getUuid() + " but the person's email was null.");
			} else {
				String message = template.getMessage().replace(EmailTemplateService.RECIPIENT_PLACEHOLDER, recipient.getName());
				TransformInlineImageDTO inlineImagesDto = transformImages(message);
				
				boolean success = emailService.sendMessage(recipient.getEmail(), template.getTitle(), inlineImagesDto.getMessage(), inlineImagesDto.getInlineImages());
				if (!success) {
					log.warn("Tried to send email template of type " + type.toString() + " to person with uuid " + recipient.getUuid() + " but the person's email was null.");
				}
			}
		}
	}
	
	private TransformInlineImageDTO transformImages(String message) {
		TransformInlineImageDTO dto = new TransformInlineImageDTO();
		List<InlineImageDTO> inlineImages = new ArrayList<>();
		Document doc = Jsoup.parse(message);

		for (Element img : doc.select("img")) {
			String src = img.attr("src");
			if (src == null || src == "") {
				continue;
			}

			InlineImageDTO inlineImageDto = new InlineImageDTO();
			inlineImageDto.setBase64(src.contains("base64"));
			
			if (!inlineImageDto.isBase64()) {
				continue;
			}
			
			String cID = UUID.randomUUID().toString();
			inlineImageDto.setCid(cID);
			inlineImageDto.setSrc(src);
			inlineImages.add(inlineImageDto);
			img.attr("src", "cid:" + cID);
		}

		dto.setInlineImages(inlineImages);
		dto.setMessage(doc.html());
		
		return dto;		
	}
}
