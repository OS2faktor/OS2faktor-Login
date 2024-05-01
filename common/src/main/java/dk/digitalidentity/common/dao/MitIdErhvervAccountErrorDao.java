package dk.digitalidentity.common.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.common.dao.model.MitIdErhvervAccountError;

public interface MitIdErhvervAccountErrorDao extends JpaRepository<MitIdErhvervAccountError, Long> {
	MitIdErhvervAccountError findById(long id);
}
