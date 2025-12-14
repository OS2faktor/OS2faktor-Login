package dk.digitalidentity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

// this allows us to use an external landing page
// note that the external JAR file needs to be extracted into a folder called landingpage for this to work
@Configuration
public class ExternalResourcesConfig {

    @Bean
    public ITemplateResolver externalTemplateResolver() {
        FileTemplateResolver resolver = new FileTemplateResolver();
        resolver.setPrefix("/shared/templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setOrder(0);
        resolver.setCheckExistence(true);
        resolver.setCacheable(true);

        return resolver;
    }
}