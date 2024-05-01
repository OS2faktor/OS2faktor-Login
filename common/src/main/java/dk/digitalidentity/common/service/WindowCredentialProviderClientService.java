package dk.digitalidentity.common.service;

import java.util.List;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.WindowCredentialProviderClientDao;
import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.WindowCredentialProviderClient;

@EnableCaching
@Service
public class WindowCredentialProviderClientService {

    @Autowired
	private WindowCredentialProviderClientDao clientDao;

    @Autowired
    private DomainService domainService;
    
    @PostConstruct
	public void init() {
		List<WindowCredentialProviderClient> wcps = clientDao.findAll();
		List<Domain> domains = domainService.getAll();
		
		for (Domain domain : domains) {
			boolean found = wcps.stream().anyMatch(w -> w.getDomain().getId() == domain.getId());
			
			if (!found) {
				WindowCredentialProviderClient newWinClient = new WindowCredentialProviderClient();
				newWinClient.setName("WinCP");
				newWinClient.setApiKey(UUID.randomUUID().toString());
				newWinClient.setDisabled(false);
				newWinClient.setDomain(domain);

				clientDao.save(newWinClient);				
			}
		}
	}

    // never needs to be reloaded
	@Cacheable("clientByApiKey")
    public WindowCredentialProviderClient getByApiKeyAndDisabledFalse(String apiKey) {
		WindowCredentialProviderClient client = clientDao.findByApiKeyAndDisabledFalse(apiKey);

		// force load, so it can be cached after session is dead
		if (client != null) {
			client.getDomain().getName();
		}
        
        return client;
    }

    public List<WindowCredentialProviderClient> getAll() {
        return clientDao.findAll();
    }

    public WindowCredentialProviderClient save(WindowCredentialProviderClient client) {
        return clientDao.save(client);
    }

    public void delete(WindowCredentialProviderClient client) {
        clientDao.delete(client);
    }
}
