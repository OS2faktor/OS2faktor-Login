package dk.digitalidentity.common.service;

import dk.digitalidentity.common.dao.KnownNetworkDao;
import dk.digitalidentity.common.dao.model.KnownNetwork;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@EnableCaching
public class KnownNetworkService {

	@Autowired
	private KnownNetworkDao knownNetworkDao;
	
	@Autowired
	private KnownNetworkService self;

	@Cacheable(value = "allKnownIps")
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
	
	@CacheEvict(value = { "allKnownIps"}, allEntries = true)
	public void cacheEvict() {
		;
	}

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void processChanges() {
    	self.cacheEvict();
    }
}
