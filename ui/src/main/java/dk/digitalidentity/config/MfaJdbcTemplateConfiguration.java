package dk.digitalidentity.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import dk.digitalidentity.common.config.CommonConfiguration;

@Configuration
public class MfaJdbcTemplateConfiguration {

	@Autowired
	private CommonConfiguration configuration;
	
	@Bean(name = "mfaTemplate")
	public JdbcTemplate mfaTemplate() {
		if (configuration.getMfaDatabase().isEnabled()) {
			HikariConfig config = new HikariConfig();
			config.setConnectionTestQuery("SELECT 1");
			config.setDriverClassName("com.mysql.cj.jdbc.Driver");
			config.setMinimumIdle(1);
			config.setMaximumPoolSize(5);
			config.setJdbcUrl(configuration.getMfaDatabase().getUrl());
			config.setPassword(configuration.getMfaDatabase().getPassword());
			config.setUsername(configuration.getMfaDatabase().getUsername());
			config.setReadOnly(true);
			config.setConnectionTimeout(5 * 1000);
			
			return new JdbcTemplate(new HikariDataSource(config));
		}
		
		return null;
	}
}
