package dk.digitalidentity.common.service;

import dk.digitalidentity.common.dao.DomainDao;
import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.Person;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
		List<Domain> domains = domainDao.findAll();
		
		domains.sort(Comparator.comparing(Domain::toString));
		
		return domains;
	}

	public List<Domain> getAllEmailTemplateDomains() {
		List<Domain> domains = domainDao.findAll();

		domains.removeIf(d -> !d.getChildDomains().isEmpty());
		domains.sort(Comparator.comparing(Domain::toString));

		return domains;
	}

	public List<Domain> getAllParents() {
		return domainDao.findAll().stream().filter(d -> d.getParent() == null).collect(Collectors.toList());
	}

	public void save(Domain domain) {
		domainDao.save(domain);
	}

	public Domain findByName(String name) {
		return domainDao.findByName(name);
	}

	public static boolean isMember(Person person, List<Domain> domains) {
		Set<Long> domainIds = domains.stream().map(d -> d.getId()).collect(Collectors.toSet());
		
		return (domainIds.contains(person.getDomain().getId()) || domainIds.contains(person.getTopLevelDomain().getId()));
	}
}
