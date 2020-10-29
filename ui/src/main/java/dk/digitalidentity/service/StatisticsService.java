package dk.digitalidentity.service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.AuditLogDao;
import dk.digitalidentity.common.dao.PersonDao;
import dk.digitalidentity.common.dao.model.AuditLog;
import dk.digitalidentity.common.dao.model.enums.LogAction;

@Service
public class StatisticsService {

	@Autowired
	private AuditLogDao auditLogDao;

	@Autowired
	private PersonDao personDao;

	@Autowired
	private StatisticsService self;

	@Cacheable("lastHourLogins")
	public List<Integer> getLoginCountLastHour() {
		List<Integer> loginCounts = new ArrayList<>();
		LocalDateTime now = LocalDateTime.now();

		for (int i = 6; i > 0; i--) {
			LocalDateTime start = now.minusMinutes(i * 10);
			LocalDateTime end = now;
			if (i != 1) {
				end = now.minusMinutes((i - 1) * 10);
			}

			loginCounts.add(findLoginsByTimePeriod(start, end).size());
		}

		return loginCounts;
	}

	@Cacheable("TotalLastHourLogins")
	public int getTotalLoginCountLastHour() {
		int total = 0;
		for (int count : getLoginCountLastHour()) {
			total += count;
		}

		return total;
	}

	@Cacheable("yesterdayLogins")
	public List<Integer> getLoginCountYesterday() {
		List<Integer> loginCounts = new ArrayList<>();
		LocalDateTime yesterday = LocalDateTime.now().minus(1, ChronoUnit.DAYS);
		LocalDateTime lastHourYesterday = yesterday.with(LocalTime.of(23, 59, 59));
		LocalDateTime firstHourYesterday = yesterday.with(LocalTime.of(0, 0, 0));

		for (int i = 1; i < 25; i++) {
			LocalDateTime start = firstHourYesterday.plusHours(i - 1);
			LocalDateTime end = firstHourYesterday.plusHours(i);
			if (i == 24) {
				end = lastHourYesterday;
			}

			loginCounts.add(findLoginsByTimePeriod(start, end).size());
		}

		return loginCounts;
	}

	@Cacheable("TotalYesterdayLogins")
	public int getTotalLoginCountYesterday() {
		int total = 0;
		for (int count : getLoginCountYesterday()) {
			total += count;
		}

		return total;
	}
	
	@Cacheable("ApprovedConditionsCount")
	public long getAprovedConditionCount() {
		return personDao.countByapprovedConditionsTrue();
	}

	@Cacheable("PersonCount")
	public long getPersonCount() {
		return personDao.count();
	}

	@Caching(evict = {
		@CacheEvict(value = "TotalYesterdayLogins", allEntries = true),
		@CacheEvict(value = "yesterdayLogins", allEntries = true)
	})
	public void cleanupDaily() {

	}

	@Caching(evict = {
		@CacheEvict(value = "lastHourLogins", allEntries = true),
		@CacheEvict(value = "totalLastHourLogins", allEntries = true),
		@CacheEvict(value = "ApprovedConditionsCount", allEntries = true),
		@CacheEvict(value = "PersonCount", allEntries = true)
	})
	public void cleanupHourly() {

	}

	@Scheduled(fixedRate = 10 * 60 * 1000)
	public void cleanUpTaskLastHourLogins() {
		self.cleanupHourly();
	}

	@Scheduled(cron = "0 4 * * * *")
	public void cleanupDailyTask() {
		self.cleanupDaily();
	}

	private List<AuditLog> findLoginsByTimePeriod(LocalDateTime start, LocalDateTime end) {
		return auditLogDao.findByTtsAfterAndTtsBeforeAndLogAction(start, end, LogAction.LOGIN);
	}
}
