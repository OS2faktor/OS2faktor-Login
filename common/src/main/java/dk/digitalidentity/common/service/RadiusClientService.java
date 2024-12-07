package dk.digitalidentity.common.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.RadiusClientDao;
import dk.digitalidentity.common.dao.model.RadiusClient;
import jakarta.transaction.Transactional;

@Service
public class RadiusClientService {

	@Autowired
	private RadiusClientDao radiusClientDao;

	public List<RadiusClient> getAll() {
		return radiusClientDao.findAll();
	}

	@Transactional
	public List<RadiusClient> getAllFullyLoaded() {
		List<RadiusClient> all = radiusClientDao.findAll();
		all.forEach(RadiusClient::loadFully);

		return all;
	}

	public RadiusClient save(RadiusClient radiusClient) {
		return radiusClientDao.save(radiusClient);
	}
	
	public RadiusClient getById(long id) {
		return radiusClientDao.findById(id);
	}
	
	public void delete(RadiusClient radiusClient) {
		radiusClientDao.delete(radiusClient);
	}
}
