package dk.digitalidentity.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.LinkDao;
import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.Link;
import dk.digitalidentity.security.SecurityUtil;

@Service
public class LinkService {

	@Autowired
	private LinkDao linkDao;
	
	@Autowired
	private SecurityUtil securityUtil;

	public List<Link> getAll() {
		return linkDao.findAll();
	}
	
	public Link getById(long id) {
		return linkDao.findById(id);
	}
	
	public List<Link> getAllForMe() {
		Domain domain = securityUtil.getTopLevelDomain();

		return getAll().stream().filter(l -> l.getDomain().getId() == domain.getId()).collect(Collectors.toList());
	}

	public Link save(Link link) {
		return linkDao.save(link);
	}

	public void deleteById(long id) {
		linkDao.deleteById(id);
	}
}
