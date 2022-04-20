package dk.digitalidentity.common.dao;

import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.Group;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;

public interface GroupDao extends JpaRepository<Group, Long> {
    Group findById(long id);
    Group findByName(String name);
    Group findByUuid(String uuid);
    List<Group> findByUuidIn(Set<String> uuids);
    List<Group> findByDomainNot(Domain domain);
}
