package dk.digitalidentity.common.config.modules;

import org.springframework.stereotype.Component;

import dk.digitalidentity.common.config.FeatureDocumentation;
import lombok.Getter;
import lombok.Setter;

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

	// if (and only if) leak-control on passwords is enabled, we will perform periodic leak-check every X days
	// during login. Note that in the future we will likely enable leak-control by default.
	private long passwordLeakCheckInterval = 7;

	// the conformityGracePeriod is how many days the end-user has to change their password after the leak has been detected,
	// after which point login will not be possible with that password, and they will need to change it using MitID
	private long passwordLeakConformityGracePeriod = 7;

	// grace-period for changing password to new complexity - if they do not do it before the deadline,
	// they will be forced to change it using MitID.
	private long passwordComplexityConformityGracePeriod = 30;
}
