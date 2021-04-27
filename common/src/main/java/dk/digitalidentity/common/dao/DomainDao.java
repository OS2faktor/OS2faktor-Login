package dk.digitalidentity.common.dao;

import dk.digitalidentity.common.dao.model.Domain;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DomainDao extends JpaRepository<Domain, Long> {
	Domain getById(Long id);
	Domain findByName(String name);
}
