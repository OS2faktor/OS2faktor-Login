package dk.digitalidentity.common.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.common.dao.model.PrivacyPolicy;

public interface PrivacyPolicyDao extends JpaRepository<PrivacyPolicy, Long> {
	List<PrivacyPolicy> findAll();
}
