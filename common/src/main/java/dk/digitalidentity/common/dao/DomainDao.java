package dk.digitalidentity.common.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.common.dao.model.Domain;

public interface DomainDao extends JpaRepository<Domain, Long> {
	Domain getById(Long id);
	Domain findByName(String name);
}
