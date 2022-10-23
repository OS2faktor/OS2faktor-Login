package dk.digitalidentity.common.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.Group;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.SchoolRoleValue;

public interface PersonDao extends JpaRepository<Person, Long> {
	Person findById(long id);
	Person findByUserId(String userId);
	List<Person> findByUuid(String uuid);
	List<Person> findByCpr(String cpr);
	List<Person> findByNemIdPid(String pid);
	List<Person> findByAdminTrueOrServiceProviderAdminTrueOrRegistrantTrueOrSupporterNotNullOrUserAdminTrue();
	List<Person> findBySamaccountName(String samAccountName);
	List<Person> findBySamaccountNameAndDomain(String samAccountName, Domain domain);
	List<Person> findBySamaccountNameAndDomainIn(String samAccountName, List<Domain> domains);
	List<Person> findByDomain(Domain domain);
	List<Person> findByDomainIn(List<Domain> domains);
	List<Person> findByNsisAllowedTrueAndDomainIn(List<Domain> domains);
	List<Person> findByDomainAndCpr(Domain domain, String cpr);
	List<Person> findByDomainAndNsisAllowed(Domain domain, boolean nsisAllowed);
	List<Person> findByDomainInAndLockedDatasetFalse(List<Domain> domains);
	List<Person> findByCprAndDomainIn(String cpr, List<Domain> domains);
	List<Person> findDistinctByGroupsGroupNotOrGroupsGroupNull(Group group);
	long countByapprovedConditionsTrue();
	List<Person> findByLockedPasswordTrue();
	List<Person> findByLockedDatasetTrue();
	List<Person> findByLockedExpiredFalseAndExpireTimestampBefore(LocalDateTime date);
	List<Person> findByLockedExpiredTrueAndExpireTimestampAfterOrExpireTimestampNull(LocalDateTime date);
	List<Person> findBySchoolRolesRoleAndDomainAndSchoolRolesInstitutionIdInAndNsisAllowedFalse(SchoolRoleValue role, Domain domain, Set<String> institutionIds);
	List<Person> findBySchoolRolesNotEmptyAndDomainIn(List<Domain> domains);
	long countByLockedPasswordTrue();
		
	@Modifying
	@Query(nativeQuery = true, value = "UPDATE persons SET daily_password_change_counter = 0")
	void resetDailyPasswordChangeCounter();
	
	@Query(nativeQuery = true, value = "SELECT DISTINCT attribute_key FROM persons_attributes")
	Set<String> findDistinctAttributeNames();
}
