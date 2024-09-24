package dk.digitalidentity.common.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.common.dao.model.CertificateChangelog;

public interface CertificateChangelogDao extends JpaRepository<CertificateChangelog, Long> {

}
