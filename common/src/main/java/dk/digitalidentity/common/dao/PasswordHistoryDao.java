package dk.digitalidentity.common.dao;

import dk.digitalidentity.common.dao.model.PasswordHistory;
import dk.digitalidentity.common.dao.model.Person;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordHistoryDao extends JpaRepository<PasswordHistory, Long> {
	List<PasswordHistory> findAll();
	List<PasswordHistory> findByPerson(Person person);
}
