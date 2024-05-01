package dk.digitalidentity.common.dao;

import dk.digitalidentity.common.dao.model.PersonStatistics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PersonStatisticsDao extends JpaRepository<PersonStatistics, Long> {
	PersonStatistics findByPersonId(Long personId);
	List<PersonStatistics> findByPersonIdIn(List<Long> peopleIds);
}
