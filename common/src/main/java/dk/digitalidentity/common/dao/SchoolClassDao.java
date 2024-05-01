package dk.digitalidentity.common.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import dk.digitalidentity.common.dao.model.SchoolClass;

public interface SchoolClassDao extends JpaRepository<SchoolClass, Long> {
	SchoolClass findByClassIdentifierAndInstitutionId(String classIdentifier, String institutionId);
	SchoolClass findById(long id);
}
