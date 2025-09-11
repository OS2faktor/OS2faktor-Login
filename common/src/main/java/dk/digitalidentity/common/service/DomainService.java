package dk.digitalidentity.common.service;

import dk.digitalidentity.common.dao.DomainDao;
import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.Person;
import jakarta.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class DomainService {
	private static final String TRUSTED_EMPLOYEE_DOMAIN_NAME = "Betroede medarbejdere";

	@Autowired
	private DomainDao domainDao;

	public Domain getById(Long id) {
		return domainDao.getById(id);
	}

	public Domain getByName(String name) {
		return getByName(name, false);
	}

	@Transactional
	public Domain getByName(String name, Consumer<Domain> consumer) {
		Domain domain = getByName(name, false);
		
		if (domain != null && consumer != null) {
			consumer.accept(domain);
		}
		
		return domain;
	}

	public Domain getByName(String name, boolean createIfNotExist) {
		Domain domain = domainDao.findByName(name);
		if (createIfNotExist && domain == null) {
			domain = new Domain(name);
			domain.setStandalone(true);
			save(domain);
		}

		return domain;
	}

	public List<Domain> getAll() {
		List<Domain> domains = domainDao.findAll();
		domains.removeIf(d -> Objects.equals(d.getName(), TRUSTED_EMPLOYEE_DOMAIN_NAME));

		domains.sort(Comparator.comparing(Domain::toString));
		
		return domains;
	}

	public List<Domain> getAllIncludingTrustedEmployees() {
		List<Domain> domains = domainDao.findAll();
		
		domains.sort(Comparator.comparing(Domain::toString));
		
		// now move the trusted employee entry to end of list
		Domain trustedEmployeeDomain = domains.stream().filter(d -> Objects.equals(d.getName(), TRUSTED_EMPLOYEE_DOMAIN_NAME)).findFirst().orElse(null);
		if (trustedEmployeeDomain != null) {
			domains.remove(trustedEmployeeDomain);
			domains.add(trustedEmployeeDomain);
		}

		return domains;
	}

	public List<Domain> getAllEmailTemplateDomains() {
		List<Domain> domains = domainDao.findAll();

		domains.removeIf(d -> !d.getChildDomains().isEmpty());
		domains.removeIf(d -> Objects.equals(d.getName(), TRUSTED_EMPLOYEE_DOMAIN_NAME));
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

	public Domain getTrustedEmployeesDomain() {
		return domainDao.findByName(TRUSTED_EMPLOYEE_DOMAIN_NAME);
	}
}
