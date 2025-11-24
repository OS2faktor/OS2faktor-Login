package dk.digitalidentity.common.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import dk.digitalidentity.common.dao.CoreDataLogDao;
import dk.digitalidentity.common.dao.model.CoreDataLog;
import dk.digitalidentity.common.dao.model.Domain;
import jakarta.transaction.Transactional;
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

	public void monitorCoreDataSync() {
		// For monitoring we are only interested in the "parent" domains, since they are the sources of data, not the sub-domains
		List<Domain> domains = domainService.getAllParents();
		for (Domain domain : domains) {
			if (domain.isStandalone()) {
				continue;
			}
			
			// find latest log entry
			CoreDataLog latestLog = coreDataLogDao.findTopByDomainOrderByTtsDesc(domain);

			LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
			if (latestLog == null || latestLog.getTts().isBefore(sevenDaysAgo)) {
				if (latestLog == null) {
					log.warn("Domain '" + domain.getName() + "' has not been updated for over 7 days (since: 'NEVER')");
				}
				else {
					log.error("Domain '" + domain.getName() + "' has not been updated for over 7 days (since: '" + latestLog.getTts() + "')");
				}
			}
			else {
				log.info("Domain '" + domain.getName() + "' was last updated: " + latestLog.getTts());
			}
		}
	}

	@Transactional // this is OK, need transaction to delete
	public void cleanup() {
		coreDataLogDao.deleteByTtsBefore(LocalDateTime.now().minusDays(30));
	}
	
	public CoreDataLog addLog(String endpoint, String domainName, String subDomain) {
		if (StringUtils.hasLength(subDomain)) {
			endpoint = endpoint + "?subdDomain=" + subDomain;
		}

		Domain domain = domainService.getByName(domainName);
		if (domain == null) {
			log.warn("Unknown domain: domainName = " + domainName + " endpoint = " + endpoint);
			return null;
		}

		return save(new CoreDataLog(endpoint, domain));
	}
}
