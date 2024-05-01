package dk.digitalidentity.common.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.MitIdErhvervAccountErrorDao;
import dk.digitalidentity.common.dao.model.MitIdErhvervAccountError;

@Service
public class MitIdErhvervAccountErrorService {

	@Autowired
	private MitIdErhvervAccountErrorDao mitIdErhvervAccountErrorDao;

	public List<MitIdErhvervAccountError> getAll() {
		return mitIdErhvervAccountErrorDao.findAll();
	}

	public MitIdErhvervAccountError save(MitIdErhvervAccountError mitIdErhvervAccountError) {
		return mitIdErhvervAccountErrorDao.save(mitIdErhvervAccountError);
	}

	public List<MitIdErhvervAccountError> saveAll(List<MitIdErhvervAccountError> mitIdErhvervAccountErrors) {
		return mitIdErhvervAccountErrorDao.saveAll(mitIdErhvervAccountErrors);
	}

	public void delete(MitIdErhvervAccountError entity) {
		mitIdErhvervAccountErrorDao.delete(entity);
	}

	public MitIdErhvervAccountError findById(long id) {
		return mitIdErhvervAccountErrorDao.findById(id);
	}
}
