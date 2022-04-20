package dk.digitalidentity.common.dao;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.common.dao.model.CoreDataLog;
import dk.digitalidentity.common.dao.model.Domain;

public interface CoreDataLogDao extends JpaRepository<CoreDataLog, Long> {
    CoreDataLog getById(Long id);

    CoreDataLog findTopByDomainOrderByTtsDesc(Domain domain);

	void deleteByTtsBefore(LocalDateTime days);
}
