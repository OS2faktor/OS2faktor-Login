package dk.digitalidentity.common.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.EmailTemplateDao;
import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.EmailTemplate;
import dk.digitalidentity.common.dao.model.EmailTemplateChild;
import dk.digitalidentity.common.dao.model.enums.EmailTemplateType;
import dk.digitalidentity.common.service.dto.InlineImageDTO;
import dk.digitalidentity.common.service.dto.TransformInlineImageDTO;

@Service
public class EmailTemplateService {
	public static final String RECIPIENT_PLACEHOLDER = "{modtager}";
	public static final String IP_PLACEHOLDER = "{ip}";
	public static final String LOG_COUNT = "{antal}";
	public static final String LIMIT = "{max}";
	public static final String LIST_OF_PERSONS = "{liste}";
	public static final String COUNTRY = "{land}";
	public static final String USERID_PLACEHOLDER = "{brugernavn}";
	
	@Autowired
	private EmailTemplateDao emailTemplateDao;

	@Autowired
	private DomainService domainService;

	public List<EmailTemplate> findForPersons() {
		List<EmailTemplate> result = new ArrayList<>();
		
		for (EmailTemplateType type : EmailTemplateType.values()) {
			if (!type.isLogWatch()) {
				result.add(findByTemplateType(type));
			}
		}
		
		return result;
	}

	public List<EmailTemplate> findForLogWatch() {
		List<EmailTemplate> result = new ArrayList<>();
		
		for (EmailTemplateType type : EmailTemplateType.values()) {
			if (type.isLogWatch()) {
				result.add(findByTemplateType(type));
			}
		}
		
		return result;
	}

	public EmailTemplate findByTemplateType(EmailTemplateType type) {
		EmailTemplate template = emailTemplateDao.findByTemplateType(type);
		if (template == null) {
			template = new EmailTemplate();
			template.setTemplateType(type);
			template.setChildren(new ArrayList<>());
			template.getChildren().addAll(generateDefaultChildren(template));
			template = emailTemplateDao.save(template);
		}
		else {
			// make sure any new domains have children generated
			List<EmailTemplateChild> children = generateDefaultChildren(template);
			if (children.size() > 0) {
				template.getChildren().addAll(children);
				template = emailTemplateDao.save(template);
			}
		}

		return template;
	}

	public List<EmailTemplateChild> generateDefaultChildren(EmailTemplate template) {
		List<EmailTemplateChild> children = new ArrayList<>();
		
		if (template.getTemplateType().isLogWatch()) {
			generateChild(template, children, null);
		}
		else {
			for (Domain domain : domainService.getAllEmailTemplateDomains()) {
				generateChild(template, children, domain);
			}
		}

		return children;
	}

	private void generateChild(EmailTemplate template, List<EmailTemplateChild> children, Domain domain) {
		EmailTemplateChild child = new EmailTemplateChild();
		String title = "Overskrift";
		String message = "Besked";

		// if we already have a child for this template/domain, just skip
		if (template.getChildren().stream().anyMatch(c -> (domain == null && c.getDomain() == null) || c.getDomain().getId() == domain.getId())) {
			return;
		}
		
		switch (template.getTemplateType()) {
			case PERSON_DEACTIVATED:
				title = "Erhvervsidentitet spærret af administrator";
				message = "Kære {modtager}\n<br/>\n<br/>Din erhvervsidentitet er blevet spærret af en administrator.";
				break;
			case PERSON_DEACTIVATION_REPEALED:
				title = "Suspendering af erhvervsidentitet ophævet";
				message = "Kære {modtager}\n<br/>\n<br/>Suspenderingen af din erhvervsidentitet er blevet ophævet af en administrator.";
				break;
			case PERSON_DEACTIVATED_CORE_DATA:
				title = "Erhvervsidentitet spærret";
				message = "Kære {modtager}\n<br/>\n<br/>Din erhvervsidentitet er blevet spærret via kommunens kildesystem.";
				break;
			case NSIS_ALLOWED:
				title = "Erhvervsidentitet tildelt";
				message = "Kære {modtager}\n<br/>\n<br/>Du har fået tildelt en erhvervsidentitet med brugernavn {brugernavn}.";
				break;
			case PASSWORD_EXPIRES:
				title = "Kodeord udløber";
				message = "Kære {modtager}\n<br/>\n<br/>Dit kodeord udløber snart. Du burde skifte det snarest muligt.";
				break;
			case PERSON_DISENFRANCHISED:
				title = "Erhvervsidentitet spærret grundet ugyldig cpr status";
				message = "Kære {modtager}\n<br/>\n<br/>Din erhvervsidentitet er blevet spærret grundet en ugyldig status i CPR registeret.";
				break;
			case TOO_MANY_PASSWORD_WRONG_NON_WHITELIST:
				title = "Antal forkert indtastede kodeord overskredet fra ukendt netadresse";
				message = "Kære {modtager}\n<br/>\n<br/> Du har indtastet forkert kodeord for mange gange fra en ukendt netadresse ({ip}). Er det ikke dig så kontakt it-afdelingen og meld hændelsen.";
				break;
			case TOO_MANY_WRONG_PASSWORD:
				title = "Antal forkerte kodeordsforsøg overskredet";
				message = "Antallet af forkerte passwordforsøg har overskredet grænsen. Der har inden for den sidste time været {antal} fejl. Undersøg om det skyldes en fejl.";
				break;
			case TOO_MANY_LOCKED_ACCOUNTS:
				title = "Overvågning af logs: For mange tids-spærrede konti";
				message = "Antallet af tids-spærrede konti har oversteget grænsen på {antal}. <br/>Der er {antal} tids-spærrede konti.";
				break;
			case TWO_COUNTRIES_ONE_HOUR:
				title = "Login fra mere end ét land indenfor én time";
				message = "En eller flere personer har logget ind fra forskellige lande indenfor den sidste time.<br/><br/>{liste}";
				break;
			case MITID_ACTIVATED:
				title = "MitID Erhverv konto aktiveret";
				message = "Kære {modtager}\n<br/>\n<br/>Du har fået aktiveret en konto inde i MitID Erhverv";
				break;
			case MITID_DEACTIVATED:
				title = "MitID Erhverv konto spærret";
				message = "Kære {modtager}\n<br/>\n<br/>Din konto i MitID Erhverv er blevet spærret";
				break;
			case NEW_LOGIN_FOREIGN_COUNTRY:
				title = "Login i nyt land registreret";
				message = "Kære {modtager}\n<br/>\n<br/>Der er registreret et nyt login fra {land}";
				break;
			case NEW_USER:
				title = "Brugerkonto oprettet";
				message = "Kære {modtager} \n<br/>\n<br/>Du er blevet tildelt en brugerkonto.<br/><br/>\n\nDit brugernavn er: {brugernavn}<br/><br/>\n\n";
		}

		child.setTitle(title);
		child.setMessage(message);
		child.setEmailTemplate(template);
		child.setDomain(domain);

		children.add(child);
	}

	public EmailTemplate save(EmailTemplate template) {
		return emailTemplateDao.save(template);
	}
	
	public EmailTemplate findById(long id) {
		return emailTemplateDao.findById(id).orElse(null);
	}

	public TransformInlineImageDTO transformImages(String message) {
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
	
	public static String safeReplacePlaceholder(String message, String placeholder, String value) {
		// as we need to put this into an HTML/XML parser, we need to escape & symbols from the value before replacing
		value = value.replace("&", "&amp;");
		
		return message.replace(placeholder, value);
	}
}
