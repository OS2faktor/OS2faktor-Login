package dk.digitalidentity.common.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.EmailTemplateDao;
import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.EmailTemplate;
import dk.digitalidentity.common.dao.model.EmailTemplateChild;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.EmailTemplateType;
import dk.digitalidentity.common.service.dto.InlineImageDTO;
import dk.digitalidentity.common.service.dto.TransformInlineImageDTO;
import jakarta.transaction.Transactional;

@Service
public class EmailTemplateService {
	// alarms
	public static final String IP_PLACEHOLDER = "{ip}";
	public static final String LOG_COUNT = "{antal}";
	public static final String LIMIT = "{max}";
	public static final String LIST_OF_PERSONS = "{liste}";
	public static final String COUNTRY = "{land}";

	// vendor mail placeholders
	public static final String IDP_URL_PLACEHOLDER = "{IDP_URL}";
	public static final String ROLLOVER_TTS_PLACEHOLDER = "{ROLLOVER_TTS}";
	public static final String EXPIRE_DATE_PLACEHOLDER = "{EXPIRE_DATE}";
	
	// users
	public static final String USERID_PLACEHOLDER = "{brugernavn}";
	public static final String NL3UUID_PLACEHOLDER = "{nemloginuuid}";
	public static final String RECIPIENT_PLACEHOLDER = "{modtager}";
	
	// full service IdP only
	public static final String MUNICIPALITY_PLACEHOLDER = "{kommune}";
	public static final String MUNICIPALITY_CONTACT_LOCATION = "{kontaktpunkt}";
	public static final String ACTIVATION_LINK_PLACEHOLDER = "{aktiveringslink}";
	public static final String LOGO_PLACEHOLDER = "{logo}";
	public static final String OPTIONAL_TEXT = "{ekstraTekst}";
	
	@Autowired
	private EmailTemplateDao emailTemplateDao;

	@Autowired
	private DomainService domainService;
	
	@Autowired
	private CommonConfiguration commonConfiguration;

	@Autowired
	private CmsMessageBundle bundle;

	public List<EmailTemplate> findForPersons() {
		List<EmailTemplate> result = new ArrayList<>();

		for (EmailTemplateType type : EmailTemplateType.values()) {
			if (type.isLogWatch()) {
				continue;
			}

			if (type.isVendorMail()) {
				continue;
			}

			if (!commonConfiguration.getFullServiceIdP().isEnabled() && type.isFullServiceIdP()) {
				continue;
			}

			result.add(findByTemplateType(type));
		}

		return result;
	}

	public List<EmailTemplate> findForLogWatch() {
		List<EmailTemplate> result = new ArrayList<>();

		for (EmailTemplateType type : EmailTemplateType.values()) {
			if (!type.isLogWatch()) {
				continue;
			}

			result.add(findByTemplateType(type));
		}

		return result;
	}

	public List<EmailTemplate> findForVendorMail() {
		List<EmailTemplate> result = new ArrayList<>();

		for (EmailTemplateType type : EmailTemplateType.values()) {
			if (!type.isVendorMail()) {
				continue;
			}

			result.add(findByTemplateType(type));
		}

		return result;
	}

	@Transactional // this is OK
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
		
		// flex children
		template.getChildren().forEach(c -> {
			if (c.getDomain() != null) {
				c.getDomain().getName();
			}
		});

		return template;
	}

	public List<EmailTemplateChild> generateDefaultChildren(EmailTemplate template) {
		List<EmailTemplateChild> children = new ArrayList<>();

		if (template.getTemplateType().isLogWatch() || template.getTemplateType().isVendorMail()) {
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
		if (template.getChildren().stream().anyMatch(c -> (domain == null && c.getDomain() == null) || (domain != null && c.getDomain() != null && c.getDomain().getId() == domain.getId()))) {
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
				message = "Kære {modtager}\n<br/>\n<br/>Du er blevet tildelt en brugerkonto.<br/><br/>\n\nDit brugernavn er: {brugernavn}<br/><br/>\n\n";
				break;
			case PASSWORD_LEAKED:
				title = "Kodeord ikke sikkert";
				message = "Kære {modtager}\n<br/>\n<br/>Dit kodeord til nedenstående brugerkonto, er blevet fundet i lister med lækkede kodeord. Det anbefales at du får skiftet dit kodeord så snart som muligt.<br/><br/>\n\nDit brugernavn er: {brugernavn}<br/><br/>\n\n";
				break;
			case FULL_SERVICE_IDP_ASSIGNED:
				// these CANNOT be edited, these are the global/final messages used by the Full Service IdP
				title = "Erhvervsidentitet tildelt";
				message = "<b>Kære {modtager}</b><br/><br/>Du har fået tildelt en erhvervsidentitet i {kommune} til brugerkontoen {brugernavn}.<br/><br/>En erhvervsidentitet er nødvendig for at tilgå en række af it-systemer som anvendes i {kommune}. Din erhvervsidentitet skal aktiveres, før den kan anvendes. Dette gøres via nedenstående link.<br/><br/>{aktiveringslink}<br/><br/>I forbindelse med aktiveringen skal du validere dig med dit MitID.<br/>{ekstraTekst}<br/>Med venlig hilsen<br/>{kontaktpunkt}<br/>{kommune}<br/>{logo}";
				break;
			case FULL_SERVICE_IDP_REMOVED:
				// these CANNOT be edited, these are the global/final messages used by the Full Service IdP
				title = "Erhvervsidentitet spærret";
				message = "<b>Kære {modtager}</b><br/><br/>Din erhvervsidentitet hos {kommune} til brugerkontoen {brugernavn} er blevet spærret.<br/><br/>Spærringen kan f.eks. skyldes:<ul><li>at du ikke længere har brug for adgangen</li><li>at du ikke har brugt din konto i længere tid, og den derfor er blevet automatisk spærret</li><li> at du ikke længere har en ansættelse eller et konsulentlignende forhold i {kommune}</li></ul>Hvis du mener, at det er en fejl, skal du henvende dig til din nærmeste leder.<br/>{ekstraTekst}<br/>Med venlig hilsen<br/>{kontaktpunkt}<br/>{kommune}<br/>{logo}<br/>";
				break;
			case PERSON_SET_RANDOM_PASSWORD :
				title = "Kodeord nulstillet";
				message = "<b>Kære {modtager}</b><br/><br/>Dit kodeord er blevet nulstillet, og du skal skifte det inden du kan logge ind igen<br/><br/>Dit brugernavn er: {brugernavn}<br/><br/>";
				break;
			case SP_VENDOR_CERTIFICATE_ROLLOVER:
				title = "Kommende certifikatskifte i OS2faktor";
				message = "<b>Til leverandøren.</b><br><br>" +
						"Dette er en automatiseret mail, sendt af OS2faktor.<br><br>" +
						"OCES certifikatet i OS2faktor udløber <b>{EXPIRE_DATE}</b>, og for at sikre en smidig overgang, er der indlæst et nyt certifikat i OS2faktor.<br><br>" +
						"Det nye certifikat ligger klar til at blive aktiveret, og aktiveringen sker automatisk <b>{ROLLOVER_TTS}</b>.<br><br>" +
						"Tjenester som I selv har koblet til OS2faktor bør automatisk opdage certifikatskiftet, da det nye certifikat allerede ligger klar i de metadata som er udstillet af OS2faktor.<br><br>" +
						"Tjenester der gør brug af OpenID Connect (OIDC) er ikke påvirket af dette certifikatskifte.<br><br>" +
						"For tjenester som ikke automatisk henter de nye certifikat-oplysninger, bør man informere dem om det planlagte certifikat-skifte. Her kan man med fordel sende dem enten linket til SAML metadata, eller det nye certifikat, så de kan klargøre skiftet i deres ende.<br><br>" +
						"<b>Certifikat</b><br>" +
						"<a href=\"{IDP_URL}/sso/saml/secondaryCert\">{IDP_URL}/sso/saml/secondaryCert</a><br><br>" +
						"<b>SAML metadata</b><br>" +
						"<a href=\"{IDP_URL}/sso/saml/metadata\">{IDP_URL}/sso/saml/metadata</a><br><br>" +
						"<b>WS-Federation metadata</b><br>" +
						"<a href=\"{IDP_URL}/ws/metadata\">{IDP_URL}/ws/metadata</a><br><br>" +
						"Hvis der er spørgsmål til det planlagte certifikatskifte, bedes I kontakte leverandøren af OS2faktor.<br><br>" +
						"Mvh<br>OS2faktor";
				break;
		}

		child.setTitle(title);
		child.setMessage(message);
		child.setEmailTemplate(template);
		child.setDomain(domain);

		// make sure eboks is enabled by default on new Full Service IdP templates
		if (child.getEmailTemplate().getTemplateType().isFullServiceIdP()) {
			child.setEboks(true);
			child.setEnabled(true);
		}

		// vendor mail templates are enabled by default
		if (child.getEmailTemplate().getTemplateType().isVendorMail()) {
			child.setEmail(true);
			child.setEnabled(true);
		}

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

	// should only really be used for the full service IdP messages (but _would_ work for the other ones, except the data might not be entirely correct, so verify that first)
	public String safeReplaceEverything(String message, Person person) {
		message = EmailTemplateService.safeReplacePlaceholder(message, EmailTemplateService.RECIPIENT_PLACEHOLDER, person.getName());
		message = EmailTemplateService.safeReplacePlaceholder(message, EmailTemplateService.USERID_PLACEHOLDER, person.getSamaccountName());
		message = EmailTemplateService.safeReplacePlaceholder(message, EmailTemplateService.NL3UUID_PLACEHOLDER, (person.getNemloginUserUuid() != null) ? person.getNemloginUserUuid() : "");
		message = EmailTemplateService.safeReplacePlaceholder(message, EmailTemplateService.ACTIVATION_LINK_PLACEHOLDER, commonConfiguration.getSelfService().getBaseUrl());
		message = EmailTemplateService.safeReplacePlaceholder(message, EmailTemplateService.MUNICIPALITY_PLACEHOLDER, commonConfiguration.getEmail().getFromName());
		message = EmailTemplateService.safeReplacePlaceholder(message, EmailTemplateService.MUNICIPALITY_CONTACT_LOCATION, commonConfiguration.getCustomer().getContactLocationForMails());
		message = EmailTemplateService.safeReplacePlaceholder(message, EmailTemplateService.OPTIONAL_TEXT, commonConfiguration.getCustomer().getOptionalText().isEmpty() ? "" : "<br/>" + commonConfiguration.getCustomer().getOptionalText() + "<br/>");

		String logo = bundle.getText("cms.logo");
		if (StringUtils.hasLength(logo)) {
			logo = "<img style=\"max-width: 30vw; max-height: 40px;\" src=\"" + logo + "\" data-filename=\"logo.png\" />";
		}
		else {
			logo = "";
		}
		
		message = EmailTemplateService.safeReplacePlaceholder(message, EmailTemplateService.LOGO_PLACEHOLDER, logo);
		
		return message;
	}
}
