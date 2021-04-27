package dk.digitalidentity.common.service;

import java.util.List;

import dk.digitalidentity.common.dao.model.Domain;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.SessionSettingDao;
import dk.digitalidentity.common.dao.model.SessionSetting;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SessionSettingService {

	@Autowired
	private SessionSettingDao sessionSettingDao;

	@Autowired
	private DomainService domainService;

	public SessionSetting getSettings(Domain domain) {
		List<SessionSetting> all = sessionSettingDao.findByDomain(domain);

		if (all.size() == 0) {
			SessionSetting settings = new SessionSetting();
			settings.setPasswordExpiry(180L);
			settings.setMfaExpiry(60L);
			settings.setDomain(domain);

			return settings;
		}
		else if (all.size() == 1) {
			return all.get(0);
		}

		log.error("More than one row with session settings for domain: " + domain.getName());
		return all.get(0);
	}

	public SessionSetting getSettings(long domainId) {
		Domain domain = domainService.getById(domainId);
		return getSettings(domain);
	}

	public SessionSetting save(SessionSetting entity) {
		return sessionSettingDao.save(entity);
	}
}
