package dk.digitalidentity.common.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import dk.digitalidentity.common.dao.model.Person;

public interface PersonDao extends JpaRepository<Person, Long> {
	Person findById(long id);
	Person findByUserId(String userId);
	List<Person> findByUuid(String uuid);
	List<Person> findByCpr(String cpr);
	List<Person> findByNemIdPid(String pid);
	List<Person> findByAdminTrueOrSupporterTrue();
	List<Person> findBySamaccountName(String samAccountName);
	List<Person> findBySamaccountNameAndDomain(String samAccountName, String domain);
	List<Person> findByDomain(String domain);
	long countByapprovedConditionsTrue();
	List<Person> findByLockedPasswordTrue();
}
