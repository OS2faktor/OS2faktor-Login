package dk.digitalidentity.common.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.EmailTemplateChildDao;
import dk.digitalidentity.common.dao.model.EmailTemplateChild;

@Service
public class EmailTemplateChildService {

	@Autowired
	private EmailTemplateChildDao emailTemplateChildDao;

	public EmailTemplateChild getById(long id) {
		return emailTemplateChildDao.findById(id);
	}

	public EmailTemplateChild save(EmailTemplateChild template) {
		return emailTemplateChildDao.save(template);
	}
}
