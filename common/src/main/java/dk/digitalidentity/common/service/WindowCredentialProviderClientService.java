package dk.digitalidentity.common.service;

import dk.digitalidentity.common.dao.WindowCredentialProviderClientDao;
import dk.digitalidentity.common.dao.model.WindowCredentialProviderClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WindowCredentialProviderClientService {

    @Autowired
	private WindowCredentialProviderClientDao clientDao;

    public WindowCredentialProviderClient getByName(String name) {
        return clientDao.findByName(name);
    }

    public WindowCredentialProviderClient getByNameAndDisabledFalse(String name) {
        return clientDao.findByNameAndDisabledFalse(name);
    }

    public List<WindowCredentialProviderClient> getAll() {
        return clientDao.findAll();
    }

    public WindowCredentialProviderClient save(WindowCredentialProviderClient sessionKey) {
        return clientDao.save(sessionKey);
    }

    public void delete(WindowCredentialProviderClient sessionKey) {
        clientDao.delete(sessionKey);
    }
}
