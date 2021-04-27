package dk.digitalidentity.common.service;

import dk.digitalidentity.common.dao.DomainDao;
import dk.digitalidentity.common.dao.model.Domain;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DomainService {

	@Autowired
	private DomainDao domainDao;

	public Domain getById(Long id) {
		return domainDao.getById(id);
	}

	public Domain getByName(String name) {
		return getByName(name, false);
	}

	public Domain getByName(String name, boolean createIfNotExist) {
		Domain domain = domainDao.findByName(name);
		if (createIfNotExist && domain == null) {
			domain = new Domain(name);
			save(domain);
		}

		return domain;
	}

	public List<Domain> getAll() {
		return domainDao.findAll();
	}

	public void save(Domain domain) {
		domainDao.save(domain);
	}
}
