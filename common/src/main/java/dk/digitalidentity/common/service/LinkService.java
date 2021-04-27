package dk.digitalidentity.common.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.LinkDao;
import dk.digitalidentity.common.dao.model.Link;

@Service
public class LinkService {

	@Autowired
	private LinkDao linkDao;

	public List<Link> getAll() {
		return linkDao.findAll();
	}

	public Link save(Link link) {
		return linkDao.save(link);
	}

	public void deleteById(long id) {
		linkDao.deleteById(id);
	}
}
