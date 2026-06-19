package dk.digitalidentity.config;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.crac.Context;
import org.crac.Core;
import org.crac.Resource;
import org.opensaml.core.config.InitializationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.saml.config.SamlConfiguration;
import jakarta.annotation.PostConstruct;

// this class solves problems with CRaC
// 1 - it handles correct restore of a DataSource, throwing away any H2 DataSource created during checkpoint, and replaces it with the correct DataSource
// 2 - it reloads all system environment settings (including any for production environment), overwriting any set during checkpoint
@Primary
@Component
@Lazy(false) // we are using lazy-loading of beans, so unless we set this, it gets ignored
public class CracAwareness implements DataSource, Resource {
    private volatile HikariDataSource delegate;

    @Autowired
    private Environment springEnvironment;

    @Autowired
    private ConfigurableEnvironment environment;
    
    @Autowired
    private CommonConfiguration commonConfiguration;

    @Autowired
    private OS2faktorConfiguration os2faktorConfiguration;
    
    @Autowired
    private SamlConfiguration samlConfiguration;

    @PostConstruct
    public void init() throws InitializationException {
        this.delegate = createDataSource();

        Core.getGlobalContext().register(this);
    }

    private HikariDataSource createDataSource() throws InitializationException {
        HikariConfig config = new HikariConfig();

        config.setJdbcUrl(getEnv("spring.datasource.url"));
        config.setUsername(getEnv("spring.datasource.username"));
        config.setPassword(getEnv("spring.datasource.password"));
        config.setMinimumIdle(getEnvInt("spring.datasource.hikari.minimum-idle"));
        config.setMaximumPoolSize(getEnvInt("spring.datasource.hikari.maximum-pool-size"));
        config.setIdleTimeout(getEnvInt("spring.datasource.hikari.idle-timeout"));
        config.setMaxLifetime(getEnvInt("spring.datasource.hikari.max-lifetime"));

        // hardcoded
    	config.setDriverClassName("com.mysql.cj.jdbc.Driver");

        return new HikariDataSource(config);
    }

    @Override
    public void beforeCheckpoint(Context<? extends Resource> context) throws Exception {
    	// ensure SAML is initialized, so we do not have to do it after restore
        SamlConfiguration.init();

        delegate.close();
    }

    @Override
    public void afterRestore(Context<? extends Resource> context) throws Exception {
        // reload system environment into Spring's property sources
        System.getenv().forEach((key, value) -> {
            System.setProperty(key, value);
        });

        // ensure configuration knows about all the loaded properties
        rebindConfiguration();

        // re-initialize Logback from its configuration file (so we can change logging settings in compose.yml)
        reloadLoggingConfiguration();

        // ensure we have a dataSource
        this.delegate = createDataSource();
    }

    // delegate all DataSource methods

    @Override
    public Connection getConnection() throws SQLException {
        return delegate.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return delegate.getConnection(username, password);
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return delegate.isWrapperFor(iface);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        delegate.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        delegate.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return delegate.getLoginTimeout();
    }

    @Override
    public java.util.logging.Logger getParentLogger() throws java.sql.SQLFeatureNotSupportedException {
        throw new java.sql.SQLFeatureNotSupportedException();
    }

    // these two methods attempt to load from environment first (which is good for a docker environment section),
    // and if that fails, falls back to the Spring property settings (which in docker is the hardcoded values
    // that are read from the build property files, and are usually not what we want)
    private String getEnv(String key) throws InitializationException {
        String value = System.getenv(key);
        if (value == null) {
        	value = System.getProperty(key);
        }
        
        if (value == null) {
            value = springEnvironment.getProperty(key);
        }

        if (value != null) {
        	return value;
        }
        
        throw new InitializationException("Missing environment value for " + key);
    }

    private int getEnvInt(String key) throws InitializationException {
        String value = System.getenv(key);
        
        if (value == null) {
        	value = System.getProperty(key);
        }

        if (value == null) {
            value = springEnvironment.getProperty(key);
        }

        if (value == null) {
        	throw new InitializationException("Missing environment value for " + key);
        }
        
        return Integer.parseInt(value);
    }
    
    private void rebindConfiguration() {
        try {
            // Load /shared/shared.properties and add it with highest priority
            org.springframework.core.io.Resource sharedPropsResource = new org.springframework.core.io.FileSystemResource("/shared/shared.properties");

            if (sharedPropsResource.exists()) {
                java.util.Properties sharedProps = new java.util.Properties();
                try (java.io.InputStream is = sharedPropsResource.getInputStream()) {
                    sharedProps.load(is);
                }

                org.springframework.core.env.MapPropertySource sharedPropsSource =
                    new org.springframework.core.env.MapPropertySource(
                        "crac-shared-properties",
                        sharedProps.entrySet().stream()
                            .collect(java.util.stream.Collectors.toMap(
                                e -> (String) e.getKey(),
                                e -> e.getValue()
                            ))
                    );

                environment.getPropertySources().addFirst(sharedPropsSource);
            }
            else {
                System.out.println("CRaC WARN: /shared/shared.properties not found, skipping.");
            }

            // add system properties as a new property source with second highest priority
            org.springframework.core.env.MapPropertySource systemProps = 
                new org.springframework.core.env.MapPropertySource(
                    "crac-restored-system-properties",
                    System.getenv().entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(
                            e -> e.getKey(),
                            e -> (Object) e.getValue()
                        ))
                );

            environment.getPropertySources()
                .addFirst(systemProps);

            // re-bind the configuration properties beans
            
            org.springframework.boot.context.properties.bind.Binder
                .get(environment)
                .bind("os2faktor.common", org.springframework.boot.context.properties.bind.Bindable.ofInstance(commonConfiguration));

            org.springframework.boot.context.properties.bind.Binder
	            .get(environment)
	            .bind("os2faktor.ui", org.springframework.boot.context.properties.bind.Bindable.ofInstance(os2faktorConfiguration));
            
            org.springframework.boot.context.properties.bind.Binder
	            .get(environment)
	            .bind("di.saml", org.springframework.boot.context.properties.bind.Bindable.ofInstance(samlConfiguration));
        }
        catch (Exception ex) {
            System.out.println("CRaC ERROR: configuration rebind failed: " + ex.getMessage());
        }
    }

	private void reloadLoggingConfiguration() {
		try {
	        LoggingSystem loggingSystem = LoggingSystem.get(getClass().getClassLoader());

	        LoggingInitializationContext initializationContext = new LoggingInitializationContext(environment);

	        loggingSystem.initialize(initializationContext, null, null);
	        
            // apply logging.level.* from the (refreshed) Spring environment.
            org.springframework.boot.context.properties.bind.Binder binder = org.springframework.boot.context.properties.bind.Binder.get(environment);

            binder.bind("logging.level", org.springframework.boot.context.properties.bind.Bindable.mapOf(String.class, String.class))
                  .ifBound(levels -> levels.forEach((logger, level) -> {
                      String name = "ROOT".equalsIgnoreCase(logger) ? null : logger;
                      org.springframework.boot.logging.LogLevel parsed = org.springframework.boot.logging.LogLevel.valueOf(level.toUpperCase());
                      loggingSystem.setLogLevel(name, parsed);
                  }));

	    }
		catch (Exception e) {
	        System.out.println("CRaC: Logback re-init failed: " + e.getMessage());
	    }
	}
}
