package dk.digitalidentity.common.config.modules;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.config.FeatureDocumentation;

@Component
@Getter
@Setter
public class FullServiceIdPConfiguration {

	@FeatureDocumentation(name = "Full Service IdP", description = "Når denne funktion er slået til, overgår OS2faktor til at være en Full Service IdP, hvilket ændrer revisionsmodellen for OS2faktor")
	private boolean enabled = false;

	// password values for NSIS domains
	private long minimumPasswordLength = 15;
	
	// session values for NSIS systems
	private long sessionExpirePassword = 180;
	private long sessionExpireMfa = 60;
}
