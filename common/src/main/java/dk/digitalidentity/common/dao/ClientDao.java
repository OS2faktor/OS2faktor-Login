package dk.digitalidentity.common.dao;

import dk.digitalidentity.common.dao.model.Client;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientDao extends JpaRepository<Client, Long> {

    Client findByApiKey(String apiKey);

}
