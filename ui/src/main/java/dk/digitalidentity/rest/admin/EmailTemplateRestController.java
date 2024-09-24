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

import dk.digitalidentity.common.dao.model.EmailTemplateChild;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.service.EmailService;
import dk.digitalidentity.common.service.EmailTemplateChildService;
import dk.digitalidentity.common.service.EmailTemplateService;
import dk.digitalidentity.common.service.dto.InlineImageDTO;
import dk.digitalidentity.mvc.admin.dto.EmailTemplateChildDTO;
import dk.digitalidentity.security.RequireAdministrator;
import dk.digitalidentity.security.SecurityUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequireAdministrator
@RestController
public class EmailTemplateRestController {
	
	@Autowired
	private EmailService emailService;
	
	@Autowired
	private SecurityUtil securityUtil;

	@Autowired
	private EmailTemplateChildService emailTemplateChildService;
	
	@Autowired
	private EmailTemplateService emailTemplateService;

	@PostMapping(value = "/admin/rest/mailtemplates")
	@ResponseBody
	public ResponseEntity<String> updateTemplate(@RequestBody EmailTemplateChildDTO emailTemplateDTO, @RequestParam("tryEmail") boolean tryEmail) {
		Person person = securityUtil.getPerson();
		if (person == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
		
		toXHTML(emailTemplateDTO);
		
		if (tryEmail) {
			String email = person.getEmail();
			if (email != null) {
				// let's try to replace what can be replaced, just to do some testing
				String message = emailTemplateDTO.getMessage();
				message = emailTemplateService.safeReplaceEverything(message, person);
				emailTemplateDTO.setMessage(message);

				List<InlineImageDTO> inlineImages = transformImages(emailTemplateDTO);

				emailService.sendMessage(email, emailTemplateDTO.getTitle(), message, inlineImages, person);
				
				return new ResponseEntity<>("Test email sendt til " + email, HttpStatus.OK);
			}
			
			return new ResponseEntity<>("Du har ingen email adresse registreret!", HttpStatus.BAD_REQUEST);
		}
		else {
			EmailTemplateChild template = emailTemplateChildService.getById(emailTemplateDTO.getId());
			if (template == null) {
				return new ResponseEntity<>(HttpStatus.NOT_FOUND);
			}

			// allow editing text on non-full-service-idp templates only
			if (!template.getEmailTemplate().getTemplateType().isFullServiceIdP()) {
				template.setMessage(emailTemplateDTO.getMessage());
				template.setTitle(emailTemplateDTO.getTitle());
			}

			if (template.getEmailTemplate().getTemplateType().isLogWatch()) {
				template.setEmail(true);
				template.setEnabled(true);
			}
			else {
				// full-service IdP templates are ALWAYS enabled
				if (!template.getEmailTemplate().getTemplateType().isFullServiceIdP()) {
					template.setEnabled(emailTemplateDTO.isEnabled());
				}
				else {
					template.setEnabled(true);
				}
				
				if (template.getEmailTemplate().getTemplateType().isEboks()) {
					template.setEboks(emailTemplateDTO.isEboksEnabled());
				}
				else {
					template.setEboks(false);
				}
				
				if (template.getEmailTemplate().getTemplateType().isEmail()) {
					template.setEmail(emailTemplateDTO.isEmailEnabled());
				}
				else {
					template.setEmail(false);
				}
				
				// make sure at least one channel is enabled
				if (!template.isEmail() && !template.isEboks()) {
					if (template.getEmailTemplate().getTemplateType().isEboks()) {
						template.setEboks(true);
					}
					else {
						template.setEmail(true);
					}
				}
			}
			
			emailTemplateChildService.save(template);
		}
		
		return new ResponseEntity<>(HttpStatus.OK);
	}
		
	private List<InlineImageDTO> transformImages(EmailTemplateChildDTO emailTemplateDTO) {
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
	private void toXHTML(EmailTemplateChildDTO emailTemplateDTO) {
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
