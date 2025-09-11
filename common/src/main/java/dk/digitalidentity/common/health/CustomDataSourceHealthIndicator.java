package dk.digitalidentity.common.health;

import javax.sql.DataSource;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CustomDataSourceHealthIndicator implements HealthIndicator {
	private final DataSource dataSource;

	@Override
	public Health health() {
		try {
			return checkDataSourceHealth();
		}
		catch (Exception ex) {
			return Health.down().withException(ex).withDetail("error", ex.getMessage()).build();
		}
	}

	private Health checkDataSourceHealth() throws Exception {
		if (dataSource instanceof HikariDataSource) {
			HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
			HikariPoolMXBean poolBean = hikariDataSource.getHikariPoolMXBean();

			if (poolBean.getThreadsAwaitingConnection() > (poolBean.getTotalConnections() / 2) + 1) {
				throw new Exception("HikariCP pool is full, and there are " + poolBean.getThreadsAwaitingConnection() + " connections waiting");
			}
		}

		return Health.up().build();
	}
}
