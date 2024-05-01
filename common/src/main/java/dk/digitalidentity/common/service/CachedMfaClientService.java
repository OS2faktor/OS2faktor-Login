package dk.digitalidentity.common.service;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.CachedMfaClientDao;

@Service
public class CachedMfaClientService {

	@Autowired
	private CachedMfaClientDao cachedMfaClientDao;
	
	@Transactional
	public void deleteBySerialnumber(String serial) {
		cachedMfaClientDao.deleteBySerialnumber(serial);
	}
}
