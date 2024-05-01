package dk.digitalidentity.common.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.common.dao.model.TUTermsAndConditions;

public interface TUTermsAndConditionsDao extends JpaRepository<TUTermsAndConditions, Long> {
	List<TUTermsAndConditions> findAll();
}
