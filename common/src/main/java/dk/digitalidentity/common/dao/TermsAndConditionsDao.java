package dk.digitalidentity.common.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.common.dao.model.TermsAndConditions;

public interface TermsAndConditionsDao extends JpaRepository<TermsAndConditions, Long> {
	List<TermsAndConditions> findAll();
}
