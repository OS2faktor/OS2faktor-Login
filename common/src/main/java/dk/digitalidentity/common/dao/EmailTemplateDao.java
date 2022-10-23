package dk.digitalidentity.common.dao;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.common.dao.model.EmailTemplate;
import dk.digitalidentity.common.dao.model.enums.EmailTemplateType;

public interface EmailTemplateDao extends CrudRepository<EmailTemplate, Long> {
	EmailTemplate findByTemplateType(EmailTemplateType type);
	List<EmailTemplate> findAll();
}