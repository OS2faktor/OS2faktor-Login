package dk.digitalidentity.common.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.common.dao.CachedMfaClientDao;
import dk.digitalidentity.common.dao.model.CachedMfaClient;
import dk.digitalidentity.common.service.mfa.model.ClientType;

@Service
public class CachedMfaClientService {

	@Autowired
	private CachedMfaClientDao cachedMfaClientDao;
	
	@Transactional // this is OK as we need transaction to save detached entity
	public void deleteBySerialnumber(String serial) {
		cachedMfaClientDao.deleteBySerialnumber(serial);
	}

	public List<CachedMfaClient> findByType(ClientType type) {
		return cachedMfaClientDao.findByType(type);
	}

	public List<CachedMfaClient> findAll() {
		return cachedMfaClientDao.findAll();
	}
}
