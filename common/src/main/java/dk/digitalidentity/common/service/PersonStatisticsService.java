package dk.digitalidentity.common.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.PersonStatisticsDao;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.PersonStatistics;

@Service
public class PersonStatisticsService {

	@Autowired
	private PersonStatisticsDao personStatisticsDao;

	public List<PersonStatistics> getAll() {
		return personStatisticsDao.findAll();
	}

	public void setLastLogin(Person person) {
		PersonStatistics statistics = getByPerson(person);
		statistics.setLastLogin(LocalDateTime.now());

		personStatisticsDao.save(statistics);
	}
	
	public void setLastSelfServiceLogin(Person person) {
		PersonStatistics statistics = getByPerson(person);
		statistics.setLastSelfServiceLogin(LocalDateTime.now());

		personStatisticsDao.save(statistics);
	}
	
	public void setLastPasswordChange(Person person) {
		PersonStatistics statistics = getByPerson(person);
		statistics.setLastPasswordChange(LocalDateTime.now());
	
		personStatisticsDao.save(statistics);
	}
	
	public void setLastUnlock(Person person) {
		PersonStatistics statistics = getByPerson(person);
		statistics.setLastUnlock(LocalDateTime.now());
	
		personStatisticsDao.save(statistics);
	}
	
	public void setLastMFAUse(Person person) {
		PersonStatistics statistics = getByPerson(person);
		statistics.setLastMFAUse(LocalDateTime.now());
	
		personStatisticsDao.save(statistics);
	}

	private PersonStatistics getByPerson(Person person) {
		PersonStatistics statistics = personStatisticsDao.findByPersonId(person.getId());
		if (statistics == null) {
			statistics = new PersonStatistics();
			statistics.setPersonId(person.getId());
		}
		
		return statistics;
	}
}
