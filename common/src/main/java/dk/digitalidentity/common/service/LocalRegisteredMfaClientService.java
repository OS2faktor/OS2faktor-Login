package dk.digitalidentity.common.service;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.LocalRegisteredMfaClientDao;
import dk.digitalidentity.common.dao.model.LocalRegisteredMfaClient;
import jakarta.validation.constraints.NotNull;

@Service
public class LocalRegisteredMfaClientService {

	@Autowired
	private LocalRegisteredMfaClientDao localRegisteredMfaClientDao;

	public List<LocalRegisteredMfaClient> getByCpr(String cpr) {
		List<LocalRegisteredMfaClient> result = localRegisteredMfaClientDao.findByCpr(cpr);
		
		Set<String> seen = new HashSet<>();
		for (Iterator<LocalRegisteredMfaClient> iterator = result.iterator(); iterator.hasNext();) {
			LocalRegisteredMfaClient localRegisteredMfaClient = iterator.next();
			
			// if already part of output, remove from resultset and delete in database
			if (!seen.add(localRegisteredMfaClient.getDeviceId())) {
				iterator.remove();

				delete(localRegisteredMfaClient);
			}
		}
		
		return result;
	}

	public List<LocalRegisteredMfaClient> getAll() {
		return localRegisteredMfaClientDao.findAll();
	}
	
	public LocalRegisteredMfaClient save(LocalRegisteredMfaClient localRegisteredMfaClient) {
		return localRegisteredMfaClientDao.save(localRegisteredMfaClient);
	}

	public LocalRegisteredMfaClient getByDeviceId(String deviceId) {
		List<LocalRegisteredMfaClient> result = localRegisteredMfaClientDao.findByDeviceId(deviceId);
		
		// due to a missing unique constraint, it is possible with some bad timing to register the same client twice (the lookup above performs cleanup ;))
		if (result.size() > 0) {
			return result.get(0);
		}
		
		return null;
	}
	
	public void delete(LocalRegisteredMfaClient localRegisteredMfaClient) {
		localRegisteredMfaClientDao.delete(localRegisteredMfaClient);
	}

	public void setPrimaryClient(LocalRegisteredMfaClient localClient, boolean setPrimary, String cpr) {
		if (setPrimary) {
			List<LocalRegisteredMfaClient> clients = localRegisteredMfaClientDao.findByCpr(cpr);
			for (LocalRegisteredMfaClient client : clients) {
				client.setPrime(client.getDeviceId().equals(localClient.getDeviceId()));
				localRegisteredMfaClientDao.saveAll(clients);
			}
		} else {
			localClient.setPrime(false);
			save(localClient);
		}
	}

	public void removeAllPrimaryClient(@NotNull String cpr) {
		List<LocalRegisteredMfaClient> clients = localRegisteredMfaClientDao.findByCpr(cpr);
		for (LocalRegisteredMfaClient client : clients) {
			client.setPrime(false);
		}
		localRegisteredMfaClientDao.saveAll(clients);
	}
}
