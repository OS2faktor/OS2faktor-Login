package dk.digitalidentity.common.service;

import dk.digitalidentity.common.dao.ClientDao;
import dk.digitalidentity.common.dao.model.Client;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@EnableCaching
public class ClientService {

    @Autowired
    private ClientDao apiTokenDao;

    public List<Client> findAll() {
        return apiTokenDao.findAll();
    }

    // note - never reloaded, as the ApiKey is not intended to change
    @Cacheable(value = "clientCache")
    public Client findByApiKey(String apiKey) {
        return apiTokenDao.findByApiKey(apiKey);
    }

    public void save(Client client) {
        apiTokenDao.save(client);
    }
}
