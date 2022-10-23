package dk.digitalidentity.common.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.SchoolClassDao;
import dk.digitalidentity.common.dao.model.SchoolClass;

@Service
public class SchoolClassService {

	@Autowired
	private SchoolClassDao schoolClassDao;
	
	public SchoolClass save(SchoolClass schoolClass) {
		return schoolClassDao.save(schoolClass);
	}
	
	public SchoolClass getByClassIdentifierAndInstitutionId(String classIdentifier, String institutionId) {
		return schoolClassDao.findByClassIdentifierAndInstitutionId(classIdentifier, institutionId);
	}

	public List<SchoolClass> getAll() {
		return schoolClassDao.findAll();
	}
	
	public void deleteAll(List<SchoolClass> toDelete) {
		schoolClassDao.deleteAll(toDelete);
	}
}
