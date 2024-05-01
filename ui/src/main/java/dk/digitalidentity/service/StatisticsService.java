package dk.digitalidentity.service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.util.Pair;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.AuditLogDao;
import dk.digitalidentity.common.dao.PersonDao;
import dk.digitalidentity.common.dao.model.AuditLog;
import dk.digitalidentity.common.dao.model.PasswordSetting;
import dk.digitalidentity.common.dao.model.enums.LogAction;
import dk.digitalidentity.common.service.ADPasswordService;
import dk.digitalidentity.common.service.PasswordSettingService;

@Service
public class StatisticsService {

	// TODO: autowire service instead, instead of direct DAO access, and recode to use count() instead of find()
	@Autowired
	private AuditLogDao auditLogDao;

	@Autowired
	private PersonDao personDao;
	
	@Autowired
	private ADPasswordService adPasswordService;
	
	@Autowired
	private PasswordSettingService passwordSettingsService;

	@Autowired
	private StatisticsService self;

	@Cacheable("websocketConnections")
	public Map<String, Pair<Integer, Integer>> getWebsocketConnections() {
		Map<String, Pair<Integer, Integer>> connectionMap = new HashMap<>();
		
		for (PasswordSetting settings : passwordSettingsService.getAllSettings()) {
			if ((settings.isReplicateToAdEnabled() || settings.isValidateAgainstAdEnabled()) && settings.getDomain().getParent() == null) {
				Pair<Integer, Integer> count = adPasswordService.getWebsocketSessionCountPair(settings.getDomain().getName());
				connectionMap.put(settings.getDomain().getName(), count);
			}
		}
		
		return connectionMap;
	}

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
		return personDao.countByApprovedConditionsTrue();
	}
	
	@Cacheable("TransferedToNemloginCount")
	public long getTransferedToNemloginCount() {
		return personDao.countByNemloginUserUuidNotNull();
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
		@CacheEvict(value = "PersonCount", allEntries = true),
		@CacheEvict(value = "TransferedToNemloginCount", allEntries = true)
	})
	public void cleanupHourly() {

	}

	@Caching(evict = {
		@CacheEvict(value = "websocketConnections", allEntries = true)
	})
	public void cleanupRealtimeValues() {

	}
	
	@Scheduled(fixedRate = 2 * 60 * 1000)
	public void cleanUpTaskRealtimeValues() {
		self.cleanupRealtimeValues();
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
