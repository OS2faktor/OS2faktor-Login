package dk.digitalidentity.common.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.common.dao.model.BadPassword;

public interface BadPasswordDao extends JpaRepository<BadPassword, Long> {
	BadPassword findByPassword(String password);
}
