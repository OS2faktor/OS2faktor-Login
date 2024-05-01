package dk.digitalidentity.common.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import dk.digitalidentity.common.dao.model.ADPasswordCache;

public interface ADPasswordCacheDao extends JpaRepository<ADPasswordCache, Long> {
	ADPasswordCache findByDomainIdAndSamAccountName(long domainId, String sAMAccountName);

	@Modifying
	@Query(nativeQuery = true, value = "DELETE FROM ad_password_cache WHERE last_updated < NOW() - INTERVAL 3 DAY")
	void deleteOld();
}
