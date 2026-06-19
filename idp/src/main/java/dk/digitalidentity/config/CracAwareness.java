package dk.digitalidentity.config;

import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.crac.Context;
import org.crac.Core;
import org.crac.Resource;
import org.flywaydb.core.Flyway;
import org.opensaml.core.config.InitializationException;
import org.opensaml.core.config.InitializationService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.server.authorization.settings.AbstractSettings;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ConfigurationSettingNames;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.config.oidc.OidcJWKSource;
import dk.digitalidentity.samlmodule.config.settings.DISAML_Configuration;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.Filter;

// this class solves problems with CRaC
// 1 - it handles correct restore of a DataSource, throwing away any H2 DataSource created during checkpoint, and replaces it with the correct DataSource
// 2 - it reloads all system environment settings (including any for production environment), overwriting any set during checkpoint
// 3 - it initializes OpenSAML, so it is ready for connections
// 4 - it performs flyway migrations
// 5 - it fixes OAuth2 Authorization Server settings fixed at checkpoint
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
    private OS2faktorConfiguration os2faktorConfiguration;
    
    @Autowired
    private CommonConfiguration commonConfiguration;
    
    @Autowired
    private DISAML_Configuration samlConfiguration;
    
    @Autowired
    private AuthorizationServerSettings authorizationServerSettings;
    
    @Autowired
    private ObjectProvider<OidcJWKSource> jwkSourceProvider;

    @Autowired
    private ApplicationContext applicationContext;
    
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
        delegate.close();
        
        jwkSourceProvider.getObject().clearCache();
    }

    @Override
    public void afterRestore(Context<? extends Resource> context) throws Exception {
        // reload system environment into Spring's property sources
        System.getenv().forEach((key, value) -> {
            System.setProperty(key, value);
        });

        InitializationService.initialize();
        
        // ensure configuration knows about all the loaded properties
        rebindConfiguration();

        // re-initialize Logback from its configuration file (so we can change logging settings in compose.yml)
        reloadLoggingConfiguration();

        // ensure we have a dataSource
        this.delegate = createDataSource();

        // create a flyway instance and perform a migrate
        flywayMigrate();
        
		// restore OAuth2 settings
		oauth2Restore();
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

	private void oauth2Restore() throws Exception {
	    String issuer = os2faktorConfiguration.getEntityId();
	    
	    // force reload the issuer into Authorization Server
	    replaceIssuer(authorizationServerSettings, issuer);
	    resetIssuerResolver(issuer);
	    
	    // clear JWK key-source
        jwkSourceProvider.getObject().clearCache();
	}
	
	private static void replaceIssuer(AuthorizationServerSettings settings, String newIssuer) throws Exception {
		// settings field is a non-mutable map, we need to inject a new map with the correct issuer
	    Field field = AbstractSettings.class.getDeclaredField("settings");
	    ReflectionUtils.makeAccessible(field);

	    @SuppressWarnings("unchecked")
	    Map<String, Object> current = (Map<String, Object>) field.get(settings);

	    // read all existing entries, then construct a fresh mutable map with the new issuer.
	    Map<String, Object> replacement = new LinkedHashMap<>(current);
	    replacement.put(ConfigurationSettingNames.AuthorizationServer.ISSUER, newIssuer);

	    field.set(settings, replacement);
	}
	
	private void resetIssuerResolver(String newIssuer) throws Exception {
	    FilterChainProxy fcp = applicationContext.getBean(FilterChainProxy.class);
	    
	    // the AuthorizationServerContextFilter caches the issuer set a build-time, so we need to overwrite it
	    for (SecurityFilterChain chain : fcp.getFilterChains()) {
	        for (Filter filter : chain.getFilters()) {
	            if (filter.getClass().getSimpleName().equals("AuthorizationServerContextFilter")) {
	                Object newResolver = buildIssuerResolver(filter, newIssuer);
	                Field resolverField = filter.getClass().getDeclaredField("issuerResolver");
	                resolverField.setAccessible(true);
	                resolverField.set(filter, newResolver);

	                return;
	            }
	        }
	    }

	    throw new IllegalStateException("CRaC ERROR: AuthorizationServerContextFilter not found in security chain");
	}
	
	private Object buildIssuerResolver(Filter filter, String newIssuer) throws Exception {
	    // issuerResolver is a private static nested class. Get its constructor reflectively.
	    Class<?> resolverClass = Class.forName("org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.AuthorizationServerContextFilter$IssuerResolver");
	    Constructor<?> ctor = resolverClass.getDeclaredConstructor(AuthorizationServerSettings.class);
	    ctor.setAccessible(true);

	    AuthorizationServerSettings tempSettings = AuthorizationServerSettings.builder()
	        .issuer(newIssuer)
	        .build();

	    return ctor.newInstance(tempSettings);
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
            
            // add system properties as a new property source with next highest
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

            // re-bind the configuration properties bean

            org.springframework.boot.context.properties.bind.Binder
                .get(environment)
                .bind("os2faktor.common", org.springframework.boot.context.properties.bind.Bindable.ofInstance(commonConfiguration));
            
            org.springframework.boot.context.properties.bind.Binder
	            .get(environment)
	            .bind("os2faktor.idp", org.springframework.boot.context.properties.bind.Bindable.ofInstance(os2faktorConfiguration));
            
            org.springframework.boot.context.properties.bind.Binder
	            .get(environment)
	            .bind("di.saml", org.springframework.boot.context.properties.bind.Bindable.ofInstance(samlConfiguration));
        }
        catch (Exception ex) {
            System.out.println("CRaC ERROR: configuration rebind failed: " + ex.getMessage());
        }
    }
    
    private void flywayMigrate() {
        Flyway.configure()
	        .dataSource(this.delegate)
	        .locations("classpath:db/migration")
	        .table("schema_version")
	        .ignoreMigrationPatterns("*:missing")
	        .load()
	        .migrate();
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
