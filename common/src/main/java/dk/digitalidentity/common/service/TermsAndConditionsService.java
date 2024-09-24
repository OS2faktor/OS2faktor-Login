package dk.digitalidentity.common.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.TermsAndConditionsDao;
import dk.digitalidentity.common.dao.model.TermsAndConditions;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@EnableCaching
@Service
public class TermsAndConditionsService {

	@Autowired
	private TermsAndConditionsDao termsAndConditionsDao;
	
	@Autowired
	private CommonConfiguration commonConfiguration;
	
	@Autowired
	private TermsAndConditionsService self;

	public TermsAndConditions getTermsAndConditions() {
		List<TermsAndConditions> all = termsAndConditionsDao.findAll();
		
		if (all.size() == 0) {
			TermsAndConditions termsAndConditions = new TermsAndConditions();
			
			if (commonConfiguration.getFullServiceIdP().isEnabled()) {
				// in full service IdP mode, the customer should setup the value manually
				termsAndConditions.setContent("");
			}
			else {
				// in non-full-service mode, we pre-populate with a useful starting text
				termsAndConditions.setContent(termsAndConditions.getFixedTerms());
			}
		
			return termsAndConditions;
		}
		else if (all.size() == 1) {
			return all.get(0);
		}
		
		log.error("More than one row with terms and agreements");
		
		return all.get(0);
	}

	public TermsAndConditions save(TermsAndConditions entity) {
		return termsAndConditionsDao.save(entity);
	}
	
	@Cacheable("LastRequiredApprovedTts")
	public LocalDateTime getLastRequiredApprovedTts() {
		return getTermsAndConditions().getMustApproveTts();
	}
	
	@Caching(evict = {
		@CacheEvict(value = "LastRequiredApprovedTts", allEntries = true)
	})
	public void cleanupCache() {

	}
	
	@Scheduled(fixedRate = 30 * 60 * 1000)
	public void cleanUpTaskRealtimeValues() {
		self.cleanupCache();
	}
}
