package dk.digitalidentity.common.dao;

import dk.digitalidentity.common.dao.model.KnownNetwork;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnownNetworkDao extends JpaRepository<KnownNetwork, Long> {

}