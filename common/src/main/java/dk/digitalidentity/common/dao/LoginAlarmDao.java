package dk.digitalidentity.common.dao;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import dk.digitalidentity.common.dao.model.LoginAlarm;
import dk.digitalidentity.common.dao.model.Person;

public interface LoginAlarmDao extends JpaRepository<LoginAlarm, Long> {

	@Modifying
	@Query(nativeQuery = true, value = "DELETE FROM login_alarms WHERE alarm_type = ?1 and tts < ?2")
	void deleteByAlarmTypeAndTtsBefore(String type, LocalDateTime before);

	long countByPersonAndIpAddress(Person person, String ipAddress);
	long countByPersonAndCountry(Person person, String country);
}
