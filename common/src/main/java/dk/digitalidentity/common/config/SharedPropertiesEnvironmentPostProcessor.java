package dk.digitalidentity.common.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;

import lombok.extern.slf4j.Slf4j;

// bit of a hack, but for AOT Cache we need to load shared.properties like this, otherwise it gets cached during the cache build
@Slf4j
public class SharedPropertiesEnvironmentPostProcessor implements EnvironmentPostProcessor {
    
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        try {
            File propertiesFile = new File("/shared/shared.properties");
            
            if (propertiesFile.exists()) {
                Properties properties = new Properties();
                try (FileInputStream fis = new FileInputStream(propertiesFile)) {
                    properties.load(fis);
                }
                
                PropertiesPropertySource propertySource = new PropertiesPropertySource("sharedProperties", properties);
                environment.getPropertySources().addFirst(propertySource);
                
                log.info("Loaded shared.properties from filesystem: " +  propertiesFile.getAbsolutePath());
            }
            else {
                log.warn("shared/shared.properties not found at: " +  propertiesFile.getAbsolutePath());
            }
        }
        catch (IOException ex) {
            log.error("Faied loading shared/shared.properties", ex);
        }
    }
}