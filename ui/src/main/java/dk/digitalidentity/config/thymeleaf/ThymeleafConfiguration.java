package dk.digitalidentity.config.thymeleaf;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.config.thymeleaf.CmsDialect;
import dk.digitalidentity.common.config.thymeleaf.CommonConfigurationDialect;
import dk.digitalidentity.common.service.CmsMessageBundle;
import dk.digitalidentity.config.OS2faktorConfiguration;

@Configuration
public class ThymeleafConfiguration {

    @Bean
    public CmsDialect cmsDialect(CmsMessageBundle cmsMessageBundle) {
        return new CmsDialect(cmsMessageBundle);
    }
    
    @Bean
    public CommonConfigurationDialect commonConfigurationDialect(CommonConfiguration commonConfiguration) {
        return new CommonConfigurationDialect(commonConfiguration);
    }
    
    @Bean
    public OS2faktorConfigurationDialect os2faktorConfigurationDialect(OS2faktorConfiguration os2faktorConfiguration) {
        return new OS2faktorConfigurationDialect(os2faktorConfiguration);
    }
}
