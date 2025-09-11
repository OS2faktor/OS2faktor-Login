package dk.digitalidentity.common.service;

import java.util.List;
import java.util.UUID;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.PasswordValidationFilterApiKeyDao;
import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.PasswordValidationFilterApiKey;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@EnableCaching
@RequiredArgsConstructor
public class PasswordValidationFilterApiKeyService {
	private final PasswordValidationFilterApiKeyDao passwordFilterDao;
	private final DomainService domainService;

	@EventListener(ApplicationReadyEvent.class)
	public void init() {
		List<PasswordValidationFilterApiKey> filters = passwordFilterDao.findAll();
		List<Domain> domains = domainService.getAll();

		for (Domain domain : domains) {
			if (domain.getParent() != null || domain.isStandalone()) {
				continue;
			}

			boolean found = filters.stream().anyMatch(w -> w.getDomain().getId() == domain.getId());

			if (!found) {
				PasswordValidationFilterApiKey newFilter = new PasswordValidationFilterApiKey();
				newFilter.setApiKey(UUID.randomUUID().toString());
				newFilter.setDisabled(false);
				newFilter.setDomain(domain);

				passwordFilterDao.save(newFilter);
			}
		}
	}

	// never needs to be reloaded
	@Cacheable("ADPasswordFilterByApiKey")
	@Transactional // both cached and < 10 ms executation, so OK
	public PasswordValidationFilterApiKey getByApiKeyAndDisabledFalse(String apiKey) {
		PasswordValidationFilterApiKey filter = passwordFilterDao.findByApiKeyAndDisabledFalse(apiKey);

		// force load, so it can be cached after session is dead
		if (filter != null) {
			Domain domain = filter.getDomain();
			if (domain != null) {
				domain.getName();

				if (domain.getChildDomains() != null) {
					domain.getChildDomains().size();
				}

				if (domain.getParent() != null) {
					domain.getParent().getName();
				}
			}
		}

		return filter;
	}

	public List<PasswordValidationFilterApiKey> getAll() {
		return passwordFilterDao.findAll();
	}

	public PasswordValidationFilterApiKey save(PasswordValidationFilterApiKey client) {
		return passwordFilterDao.save(client);
	}

	public void delete(PasswordValidationFilterApiKey client) {
		passwordFilterDao.delete(client);
	}
}
