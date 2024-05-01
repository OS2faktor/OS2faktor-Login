package dk.digitalidentity.config.modules;

import dk.digitalidentity.common.config.FeatureDocumentation;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
public class UserAdminstrationConfig {
	
    @FeatureDocumentation(name = "Bruger API", description = "API til sætte tvungen kodeordsskifte på brugere")
    private boolean enabled = false;
    
    private String apiKey;
}
