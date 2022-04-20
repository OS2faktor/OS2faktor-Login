package dk.digitalidentity.common.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.CmsMessageDao;
import dk.digitalidentity.common.dao.model.CmsMessage;

@EnableScheduling
@Service
public class CmsMessageService {
	private LocalDateTime lastCheckedForUpdates = LocalDateTime.now();
	private Map<String, String> cmsMap = new HashMap<>();

	@Autowired
	private CmsMessageDao cmsMessageDao;
	
	public CmsMessage save(CmsMessage cmsMessage) {
		return cmsMessageDao.save(cmsMessage);
	}
	
	public CmsMessage getByCmsKey(String key) {
		return cmsMessageDao.findByCmsKey(key);
	}

	public void deleteById(long id) {
		cmsMessageDao.deleteById(id);
	}
	
	public Map<String, String> getCmsMap() {
		return cmsMap;
	}
	
	@Scheduled(fixedRate = 5 * 60 * 1000)
	public void updateCmsMap() {
		if (cmsMap == null || cmsMap.isEmpty()) {
			cmsMap = cmsMessageDao.findAll().stream().collect(Collectors.toMap(CmsMessage::getCmsKey, CmsMessage::getCmsValue));
		}
		else {
			List<CmsMessage> cmsMessages = cmsMessageDao.findAllByLastUpdatedAfter(lastCheckedForUpdates.minusMinutes(1L));
			lastCheckedForUpdates = LocalDateTime.now();

			// any changes?
			if (cmsMessages.size() > 0) {
				Map<String, String> newMap = new HashMap<>(cmsMap);

				for (CmsMessage cmsMessage : cmsMessages) {
					newMap.put(cmsMessage.getCmsKey(), cmsMessage.getCmsValue());
				}

				// swapperoo - do not modify directly to avoid access issues
				cmsMap = newMap;
			}
		}
	}
}
