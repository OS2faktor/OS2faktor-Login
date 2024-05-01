package dk.digitalidentity.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.BadPasswordDao;
import dk.digitalidentity.common.dao.model.BadPassword;

@Service
public class BadPasswordService {

	@Autowired
	private BadPasswordDao badPasswordDao;
	
	public List<BadPassword> getAll() {
		return badPasswordDao.findAll();
	}

	public void delete(long id) {
		badPasswordDao.deleteById(id);
	}
	
	public boolean exists(String badPassword) {
		return (badPasswordDao.findByPassword(badPassword).size() > 0);
	}

	public BadPassword save(BadPassword badPassword) {
		return badPasswordDao.save(badPassword);
	}
}
