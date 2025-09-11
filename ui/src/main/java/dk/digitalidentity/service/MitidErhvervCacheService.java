package dk.digitalidentity.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.common.dao.MitidErhvervCacheDao;
import dk.digitalidentity.common.dao.model.MitidErhvervCache;
import dk.digitalidentity.nemlogin.service.model.Employee;
import jakarta.transaction.Transactional;

@Service
public class MitidErhvervCacheService {

	@Autowired
	private MitidErhvervCacheDao mitidErhvervCacheDao;
	
	@Transactional // this is OK, just want to fetch in an isolated transaction
	public List<MitidErhvervCache> findAll() {
		return mitidErhvervCacheDao.findAll();
	}

	public void save(MitidErhvervCache mitidErhvervCache) {
		mitidErhvervCache.setLastUpdated(LocalDateTime.now());
		mitidErhvervCacheDao.save(mitidErhvervCache);
	}

	@Transactional // this is OK, required to save
	public void saveAll(List<MitidErhvervCache> mitidErhvervCaches) {
		if (mitidErhvervCaches != null) {
			for (MitidErhvervCache mitidErhvervCache :  mitidErhvervCaches) {
				mitidErhvervCache.setLastUpdated(LocalDateTime.now());
			}
		}
		
		mitidErhvervCacheDao.saveAll(mitidErhvervCaches);
	}
	
	@Transactional // this is OK, required to delete
	public void delete(MitidErhvervCache mitidErhvervCache) {
		mitidErhvervCacheDao.delete(mitidErhvervCache);
	}
	
	public MitidErhvervCache fromEmployee(Employee employee) {
		MitidErhvervCache cache = new MitidErhvervCache();
		cache.setCpr(employee.getProfile() != null ? employee.getProfile().getCprNumber() : null);
		cache.setEmail(employee.getEmailAddress());
		cache.setGivenname(employee.getProfile() != null ? employee.getProfile().getGivenName() : null);
		cache.setLocalCredential(employee.getEmployeeCredentials() != null ? employee.getEmployeeCredentials().stream().anyMatch(c -> Objects.equals("Local", c.getId())) : false);
		cache.setMitidErhvervId(employee.getId());
		cache.setMitidPrivatCredential(employee.getEmployeeCredentials() != null ? employee.getEmployeeCredentials().stream().anyMatch(c -> Objects.equals("PrivateMitID", c.getId())) : false);
		cache.setRid(employee.getRid());
		cache.setStatus(employee.getStatus());
		cache.setSurname(employee.getProfile() != null ? employee.getProfile().getSurname() : null);
		cache.setUuid(employee.getUuid());
		cache.setQualifiedSignature(employee.isQualifiedSignature());
		
		return cache;
	}

	public MitidErhvervCache findByUuid(String uuid) {
		return mitidErhvervCacheDao.findByUuid(uuid);
	}
}
