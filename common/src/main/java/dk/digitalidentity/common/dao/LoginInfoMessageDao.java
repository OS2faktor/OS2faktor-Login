package dk.digitalidentity.common.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.common.dao.model.LoginInfoMessage;

public interface LoginInfoMessageDao extends JpaRepository<LoginInfoMessage, Long> {
	List<LoginInfoMessage> findAll();
}
