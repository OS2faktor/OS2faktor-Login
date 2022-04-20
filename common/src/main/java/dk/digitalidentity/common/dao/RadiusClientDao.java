package dk.digitalidentity.common.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.common.dao.model.RadiusClient;

public interface RadiusClientDao extends JpaRepository<RadiusClient, Long> {
	RadiusClient findById(long id);
	List<RadiusClient> findAll();
}
