package dk.digitalidentity.common.dao;

import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.Supporter;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupporterDao extends JpaRepository<Supporter, Long> {
	Domain getById(Long id);
}
