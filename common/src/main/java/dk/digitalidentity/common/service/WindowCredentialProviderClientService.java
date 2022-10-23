package dk.digitalidentity.common.service;

import dk.digitalidentity.common.dao.WindowCredentialProviderClientDao;
import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.WindowCredentialProviderClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import javax.annotation.PostConstruct;

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
			if (domain.getName().equals("OS2faktor")) {
				continue;
			}

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

    public WindowCredentialProviderClient getByApiKeyAndDisabledFalse(String apiKey) {
        return clientDao.findByApiKeyAndDisabledFalse(apiKey);
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
