package dk.digitalidentity.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:shared.properties", ignoreResourceNotFound = true)
public class SharedConfigurationReader {

}
