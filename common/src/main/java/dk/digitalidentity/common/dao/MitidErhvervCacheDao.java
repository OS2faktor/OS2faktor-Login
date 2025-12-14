package dk.digitalidentity.common.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.common.dao.model.MitidErhvervCache;

public interface MitidErhvervCacheDao extends JpaRepository<MitidErhvervCache, Long> {
	List<MitidErhvervCache> findAll();

	MitidErhvervCache findByUuid(String uuid);

	List<MitidErhvervCache> findByCpr(String cpr);
}
