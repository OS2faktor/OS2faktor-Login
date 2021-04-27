package dk.digitalidentity.common.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.common.dao.model.Link;

public interface LinkDao extends JpaRepository<Link, Long> {
	List<Link> findAll();
}
