package dk.digitalidentity.rest.admin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.htmlcleaner.BrowserCompactXmlSerializer;
import org.htmlcleaner.CleanerProperties;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.common.dao.model.EmailTemplate;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.service.EmailService;
import dk.digitalidentity.common.service.EmailTemplateService;
import dk.digitalidentity.common.service.dto.InlineImageDTO;
import dk.digitalidentity.mvc.admin.dto.EmailTemplateDTO;
import dk.digitalidentity.security.RequireAdministrator;
import dk.digitalidentity.security.SecurityUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequireAdministrator
@RestController
public class EmailTemplateRestController {

	@Autowired
	private  EmailTemplateService emailTemplateService;
	
	@Autowired
	private EmailService emailService;
	
	@Autowired
	private SecurityUtil securityUtil;

	@PostMapping(value = "/admin/rest/mailtemplates")
	@ResponseBody
	public ResponseEntity<String> updateTemplate(@RequestBody EmailTemplateDTO emailTemplateDTO, @RequestParam("tryEmail") boolean tryEmail) {
		Person person = securityUtil.getPerson();
		if (person == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
		
		toXHTML(emailTemplateDTO);
		
		if (tryEmail) {
			String email = person.getEmail();
			if (email != null) {
				List<InlineImageDTO> inlineImages = transformImages(emailTemplateDTO);
				emailService.sendMessage(email, emailTemplateDTO.getTitle(), emailTemplateDTO.getMessage(), inlineImages);
				
				return new ResponseEntity<>("Test email sendt til " + email, HttpStatus.OK);
			}
			
			return new ResponseEntity<>("Du har ingen email adresse registreret!", HttpStatus.BAD_REQUEST);
		}
		else {
			EmailTemplate template = emailTemplateService.findById(emailTemplateDTO.getId());
			if (template == null) {
				return new ResponseEntity<>(HttpStatus.NOT_FOUND);
			}

			template.setMessage(emailTemplateDTO.getMessage());
			template.setTitle(emailTemplateDTO.getTitle());
			template.setEnabled(emailTemplateDTO.isEnabled());
			
			if (template.getTemplateType().isEboks()) {
				template.setEboks(emailTemplateDTO.isEboksEnabled());
			} else {
				template.setEboks(false);
			}
			
			if (template.getTemplateType().isEmail()) {
				template.setEmail(emailTemplateDTO.isEmailEnabled());
			} else {
				template.setEmail(false);
			}
			
			emailTemplateService.save(template);
		}
		
		return new ResponseEntity<>(HttpStatus.OK);
	}
		
	private List<InlineImageDTO> transformImages(EmailTemplateDTO emailTemplateDTO) {
		List<InlineImageDTO> inlineImages = new ArrayList<>();
		String message = emailTemplateDTO.getMessage();
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

		emailTemplateDTO.setMessage(doc.html());
		
		return inlineImages;
	}
	
	/**
	 * summernote does not generate valid XHTML. At least the <br/> and <img/> tags are not closed,
	 * so we need to close them, otherwise our PDF processing will fail.
	 */
	private void toXHTML(EmailTemplateDTO emailTemplateDTO) {
		String message = emailTemplateDTO.getMessage();
		if (message != null) {
			try {
				CleanerProperties properties = new CleanerProperties();
				properties.setOmitXmlDeclaration(true);
				TagNode tagNode = new HtmlCleaner(properties).clean(message);
			
				ByteArrayOutputStream bos = new ByteArrayOutputStream();
				new BrowserCompactXmlSerializer(properties).writeToStream(tagNode, bos);
	
				emailTemplateDTO.setMessage(new String(bos.toByteArray(), Charset.forName("UTF-8")));
			}
			catch (IOException ex) {
				log.error("could not parse: " + emailTemplateDTO.getMessage());
			}
		}
	}
}
