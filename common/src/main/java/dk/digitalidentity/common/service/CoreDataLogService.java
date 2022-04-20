package dk.digitalidentity.common.service;

import java.time.LocalDateTime;
import java.util.List;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.CoreDataLogDao;
import dk.digitalidentity.common.dao.model.CoreDataLog;
import dk.digitalidentity.common.dao.model.Domain;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CoreDataLogService {

	@Autowired
	private CoreDataLogDao coreDataLogDao;

	@Autowired
	private DomainService domainService;

	public CoreDataLog getById(long id) {
		return coreDataLogDao.getById(id);
	}

	public CoreDataLog save(CoreDataLog domain) {
		return coreDataLogDao.save(domain);
	}

	@Transactional
	public void monitorCoreDataSync() {
		// For monitoring we are only interested in the "parent" domains, since they are the sources of data, not the sub-domains
		List<Domain> domains = domainService.getAllParents();
		for (Domain domain : domains) {
			if (!domain.isMonitored()) {
				log.info("Skipping domain: '" + domain.getName() + "'. monitoring = false");
				continue;
			}
			
			// cleanup old log entries
			coreDataLogDao.deleteByTtsBefore(LocalDateTime.now().minusDays(30));

			// find latest log entry
			CoreDataLog latestLog = coreDataLogDao.findTopByDomainOrderByTtsDesc(domain);

			LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
			if (latestLog == null || latestLog.getTts().isBefore(sevenDaysAgo)) {
				log.error("Domain '" + domain.getName() + "' has not been updated for over 7 days (since: '" + (latestLog == null ? "NEVER" : latestLog.getTts()) + "')");
			}
			else {
				log.info("Domain '" + domain.getName() + "' was last updated: " + latestLog.getTts());
			}
		}
	}

	public CoreDataLog addLog(String endpoint, String domainName) {
		Domain domain = domainService.getByName(domainName);

		return save(new CoreDataLog(endpoint, domain));
	}
}
