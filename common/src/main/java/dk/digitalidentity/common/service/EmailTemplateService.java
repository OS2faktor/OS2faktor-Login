package dk.digitalidentity.common.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.EmailTemplateDao;
import dk.digitalidentity.common.dao.model.EmailTemplate;
import dk.digitalidentity.common.dao.model.enums.EmailTemplateType;

@Service
public class EmailTemplateService {
	public static final String RECIPIENT_PLACEHOLDER = "{modtager}";
	
	@Autowired
	private EmailTemplateDao emailTemplateDao;

	public List<EmailTemplate> findAll() {
		List<EmailTemplate> result = new ArrayList<>();
		
		for (EmailTemplateType type : EmailTemplateType.values()) {
			result.add(findByTemplateType(type));
		}
		
		return result;
	}

	public EmailTemplate findByTemplateType(EmailTemplateType type) {
		EmailTemplate template = emailTemplateDao.findByTemplateType(type);
		if (template == null) {
			template = new EmailTemplate();
			String title = "Overskrift";
			String message = "Besked";
			
			switch (type) {
				case PERSON_DEACTIVATED:
					title = "Erhvervsidentitet spærret af administrator";
					message = "Kære {modtager}\n<br/>\n<br/>Din erhvervsidentitet er blevet spærret af en administrator.";
					break;
				case PERSON_SUSPENDED:
					title = "Erhvervsidentitet suspenderet af administrator";
					message = "Kære {modtager}\n<br/>\n<br/>Din erhvervsidentitet er blevet suspenderet af en administrator.";
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
					message = "Kære {modtager}\n<br/>\n<br/>Du har fået tildelt en erhvervsidentitet.";
					break;
			}
			
			template.setTitle(title);
			template.setMessage(message);
			template.setTemplateType(type);
			
			template = emailTemplateDao.save(template);
		}
		
		return template;
	}

	public EmailTemplate save(EmailTemplate template) {
		return emailTemplateDao.save(template);
	}
	
	public EmailTemplate findById(long id) {
		return emailTemplateDao.findById(id).orElse(null);
	}
}
