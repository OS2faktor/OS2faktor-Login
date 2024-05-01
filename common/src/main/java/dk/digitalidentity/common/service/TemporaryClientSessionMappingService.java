package dk.digitalidentity.common.service;

import dk.digitalidentity.common.dao.TemporaryClientSessionMappingDao;
import dk.digitalidentity.common.dao.model.TemporaryClientSessionKey;
import dk.digitalidentity.common.dao.model.mapping.TemporaryClientSessionMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TemporaryClientSessionMappingService {

	@Autowired
	private TemporaryClientSessionMappingDao temporaryClientSessionMappingDao;

	public List<TemporaryClientSessionMapping> getByToken(String token) {
		return temporaryClientSessionMappingDao.findByTemporaryClientSessionKey(token);
	}

	public List<TemporaryClientSessionMapping> getByTemporaryClientSessionKey(TemporaryClientSessionKey clientSessionKey) {
		return temporaryClientSessionMappingDao.findByTemporaryClient(clientSessionKey);
	}
	
	public TemporaryClientSessionMapping save(TemporaryClientSessionMapping temporaryClientSessionMapping) {
		return temporaryClientSessionMappingDao.save(temporaryClientSessionMapping);
	}
}
