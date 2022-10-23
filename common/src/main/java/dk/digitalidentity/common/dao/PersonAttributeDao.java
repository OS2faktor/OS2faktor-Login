package dk.digitalidentity.common.dao;

import dk.digitalidentity.common.dao.model.PersonAttribute;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonAttributeDao extends JpaRepository<PersonAttribute, Long> {
	PersonAttribute findById(long id);
	PersonAttribute findByName(String name);
}
