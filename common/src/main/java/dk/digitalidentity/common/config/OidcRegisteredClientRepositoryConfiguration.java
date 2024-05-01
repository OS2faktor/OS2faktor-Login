package dk.digitalidentity.common.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

@Configuration
public class OidcRegisteredClientRepositoryConfiguration {

	@Autowired
	@Qualifier("defaultTemplate")
	private JdbcTemplate jdbcTemplate;

	@Bean
	public RegisteredClientRepository registeredClientRepository() {
		return new CustomJdbcRegisteredClientRepository(jdbcTemplate);
	}
}
