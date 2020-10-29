package dk.digitalidentity.common.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.TermsAndConditionsDao;
import dk.digitalidentity.common.dao.model.TermsAndConditions;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TermsAndConditionsService {

	@Autowired
	private TermsAndConditionsDao termsAndConditionsDao;

	public TermsAndConditions getTermsAndConditions() {
		List<TermsAndConditions> all = termsAndConditionsDao.findAll();
		
		if (all.size() == 0) {
			TermsAndConditions termsAndConditions = new TermsAndConditions();
			termsAndConditions.setContent("");
		
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
}
