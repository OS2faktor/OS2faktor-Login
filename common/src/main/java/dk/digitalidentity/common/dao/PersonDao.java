package dk.digitalidentity.common.dao;

import java.util.List;

import dk.digitalidentity.common.dao.model.Domain;
import org.springframework.data.jpa.repository.JpaRepository;
import dk.digitalidentity.common.dao.model.Person;

public interface PersonDao extends JpaRepository<Person, Long> {
	Person findById(long id);
	Person findByUserId(String userId);
	List<Person> findByUuid(String uuid);
	List<Person> findByCpr(String cpr);
	List<Person> findByNemIdPid(String pid);
	List<Person> findByAdminTrueOrRegistrantTrueOrSupporterNotNull();
	List<Person> findBySamaccountName(String samAccountName);
	List<Person> findBySamaccountNameAndDomain(String samAccountName, Domain domain);
	List<Person> findByDomain(Domain domain);
	List<Person> findByDomainAndCpr(Domain domain, String cpr);
	long countByapprovedConditionsTrue();
	List<Person> findByLockedPasswordTrue();
}
