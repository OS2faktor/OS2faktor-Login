package dk.digitalidentity.common.log.querycount;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DelegatingDataSource;

public class QueryCountingDataSource extends DelegatingDataSource {
	private static final ThreadLocal<AtomicInteger> COUNT = ThreadLocal.withInitial(AtomicInteger::new);

	public QueryCountingDataSource(DataSource delegate) {
		super(delegate);
	}

	public static void start() {
		COUNT.get().set(0);
	}

	public static int getCount() {
		return COUNT.get().get();
	}

	public static void clear() {
		COUNT.remove();
	}

	@Override
	public Connection getConnection() throws SQLException {
		return proxyConnection(super.getConnection());
	}

	@Override
	public Connection getConnection(String username, String password) throws SQLException {
		return proxyConnection(super.getConnection(username, password));
	}

	private Connection proxyConnection(Connection real) {
		return (Connection) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class[] { Connection.class }, (_, method, args) -> {
			String name = method.getName();
			if (name.startsWith("prepareStatement") || name.startsWith("prepareCall") || name.equals("createStatement")) {
				COUNT.get().incrementAndGet();
			}
			
			return method.invoke(real, args);
		});
	}
}
