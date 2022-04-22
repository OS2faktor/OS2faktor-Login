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
	private TermsAndConditionsService self;

	public TermsAndConditions getTermsAndConditions() {
		List<TermsAndConditions> all = termsAndConditionsDao.findAll();
		
		if (all.size() == 0) {
			TermsAndConditions termsAndConditions = new TermsAndConditions();
			termsAndConditions.setContent("<p>Jeg medgiver hermed at at være indeforstået med nedenstående vilkår for anvendelsen af erhvervsidentiten</p><ul><li>At jeg ved aktiveringen af erhvervsidentiten oplyser fyldestgørende og retvisende identifikationsinformationer</li><li>At jeg ikke deler erhvervsidentiteten med andre</li><li>At jeg holder kodeord og andre loginmidler tilknyttet erhvervsidentiteten fortrolig</li><li>At jeg omgående spærrer erhvervsidentiten, eller at jeg skifter kodeord og andre loginmidler,&nbsp;ved mistanke om at erhvervsidentiteten er blevet kompromiteret</li><li>At jeg omgående anmoder om at få min erhvervsidentiten genudstedt hvis de tilknyttede identitets-data (fx personnummer) har ændret sig siden udstedelsen</li></ul><p>Jeg medgiver samtidig at jeg er bekendt med kommunens informationssikkerhedspolitikker, og følger disse, og at jeg er ansvarlig for løbende at holde mig opdateret omkring ændringer i informationsikkerhedspolitikken.</p><p>Endeligt er jeg bekendt med at jeg kun må anvende erhvervsidentiten i forbindelse med mit arbejdsmæssige hverv.</p>");
		
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
