package dk.digitalidentity.common.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.LocalRegisteredMfaClientDao;
import dk.digitalidentity.common.dao.model.LocalRegisteredMfaClient;

@Service
public class LocalRegisteredMfaClientService {

	@Autowired
	private LocalRegisteredMfaClientDao localRegisteredMfaClientDao;

	public List<LocalRegisteredMfaClient> getByCpr(String cpr) {
		return localRegisteredMfaClientDao.findByCpr(cpr);
	}

	public LocalRegisteredMfaClient save(LocalRegisteredMfaClient localRegisteredMfaClient) {
		return localRegisteredMfaClientDao.save(localRegisteredMfaClient);
	}

	public LocalRegisteredMfaClient getByDeviceId(String deviceId) {
		return localRegisteredMfaClientDao.findByDeviceId(deviceId);
	}
	
	public void delete(LocalRegisteredMfaClient localRegisteredMfaClient) {
		localRegisteredMfaClientDao.delete(localRegisteredMfaClient);
	}
}
