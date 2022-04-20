package dk.digitalidentity.common.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.common.dao.model.KombitSubsystem;

public interface KombitSubsystemDao extends JpaRepository<KombitSubsystem, Long> {
	List<KombitSubsystem> findByDeletedFalse();
	KombitSubsystem findByEntityId(String entityId);
}
