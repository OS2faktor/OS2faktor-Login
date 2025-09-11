package dk.digitalidentity.common.service;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.LoginAlarmDao;
import dk.digitalidentity.common.dao.model.LoginAlarm;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.LoginAlarmType;
import jakarta.transaction.Transactional;

@Service
public class LoginAlarmService {

	@Autowired
	private LoginAlarmDao loginAlarmDao;

	public LoginAlarm save(LoginAlarm alarm) {
		return loginAlarmDao.save(alarm);
	}
	
	@Transactional // this is OK as we have an isolated delete operation
	public void deleteOldCountryAlarms() {
		loginAlarmDao.deleteByAlarmTypeAndTtsBefore(LoginAlarmType.COUNTRY.toString(), LocalDateTime.now().minusYears(1));
	}
	
	@Transactional // this is OK as we have an isolated delete operation
	public void deleteOldIpAddressAlarms() {
		loginAlarmDao.deleteByAlarmTypeAndTtsBefore(LoginAlarmType.IP_ADDRESS.toString(), LocalDateTime.now().minusMonths(1));
	}

	public long countByPersonAndIpAddress(Person person, String ipAddress) {
		return loginAlarmDao.countByPersonAndIpAddress(person, ipAddress);
	}
	
	public long countByPersonAndCountry(Person person, String country) {
		return loginAlarmDao.countByPersonAndCountry(person, country);
	}
}
