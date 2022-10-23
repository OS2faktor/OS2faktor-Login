package dk.digitalidentity.common.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.common.dao.model.Domain;

public interface DomainDao extends JpaRepository<Domain, Long> {
	Domain getById(Long id);
	Domain findByName(String name);
	List<Domain> findAll();
}
