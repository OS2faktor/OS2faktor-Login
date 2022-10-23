package dk.digitalidentity.common.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.common.dao.model.BadPassword;

public interface BadPasswordDao extends JpaRepository<BadPassword, Long> {
	List<BadPassword> findByPassword(String password);
}
