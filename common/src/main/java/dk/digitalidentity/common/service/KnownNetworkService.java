package dk.digitalidentity.common.service;

import dk.digitalidentity.common.dao.KnownNetworkDao;
import dk.digitalidentity.common.dao.model.KnownNetwork;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class KnownNetworkService {

	@Autowired
	private KnownNetworkDao knownNetworkDao;

	public List<String> getAllIPs() {
		return knownNetworkDao.findAll().stream().map(KnownNetwork::getIp).collect(Collectors.toList());
	}

	public List<KnownNetwork> getAll() {
		return knownNetworkDao.findAll();
	}

	public KnownNetwork save(KnownNetwork knownNetwork) {
		return knownNetworkDao.save(knownNetwork);
	}

	public List<KnownNetwork> saveAll(List<KnownNetwork> KnownNetworks) {
		return knownNetworkDao.saveAll(KnownNetworks);
	}

	public void delete(KnownNetwork knownNetwork) {
		knownNetworkDao.delete(knownNetwork);
	}

	public void deleteAll(List<KnownNetwork> KnownNetworks) {
		knownNetworkDao.deleteAll(KnownNetworks);
	}
}
