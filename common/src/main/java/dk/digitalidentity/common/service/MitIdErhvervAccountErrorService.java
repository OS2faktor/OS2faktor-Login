package dk.digitalidentity.common.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.MitIdErhvervAccountErrorDao;
import dk.digitalidentity.common.dao.model.MitIdErhvervAccountError;
import jakarta.transaction.Transactional;

@Service
public class MitIdErhvervAccountErrorService {

	@Autowired
	private MitIdErhvervAccountErrorDao mitIdErhvervAccountErrorDao;

	@Transactional // this is OK, need to fetch all relevant data
	public List<MitIdErhvervAccountError> getAll() {
		List<MitIdErhvervAccountError> errors = mitIdErhvervAccountErrorDao.findAll();
		
		errors.forEach(e -> {
			if (e.getPerson() != null) {
				e.getPerson().getDomain().getName();
			}
		});
		
		return errors;
	}

	public MitIdErhvervAccountError save(MitIdErhvervAccountError mitIdErhvervAccountError) {
		return mitIdErhvervAccountErrorDao.save(mitIdErhvervAccountError);
	}

	@Transactional // this is OK, we need one to save
	public List<MitIdErhvervAccountError> saveAll(List<MitIdErhvervAccountError> mitIdErhvervAccountErrors) {
		return mitIdErhvervAccountErrorDao.saveAll(mitIdErhvervAccountErrors);
	}

	@Transactional // this is OK, we need one to delete
	public void delete(MitIdErhvervAccountError entity) {
		mitIdErhvervAccountErrorDao.delete(entity);
	}

	public MitIdErhvervAccountError findById(long id) {
		return mitIdErhvervAccountErrorDao.findById(id);
	}
}
