package dk.digitalidentity.common.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.common.dao.model.EmailTemplateChild;

public interface EmailTemplateChildDao extends JpaRepository<EmailTemplateChild, Long> {
	EmailTemplateChild findById(long id);
}
