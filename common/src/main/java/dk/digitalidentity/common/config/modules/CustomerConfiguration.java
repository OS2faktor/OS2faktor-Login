package dk.digitalidentity.common.config.modules;

import org.springframework.stereotype.Component;

import dk.digitalidentity.common.config.FeatureDocumentation;
import lombok.Getter;
import lombok.Setter;

@Component
@Getter
@Setter
public class CustomerConfiguration {
	private String cvr;
	
	@FeatureDocumentation(name = "MFA admin-registrering", description = "Gør det muligt at tildele MFA-registrant rollen til administratorer, så der kan tildeles MFA klienter (chrome, edge, windows, ios og android) til brugere uden MitID aktivering. Disse MFA klienter kan IKKE bruges til at lave et NSIS login med")
	private boolean enableRegistrant = false;

	private boolean enableUnlockAccount = true;
	
	private boolean showRegisterMfaClient = false;

	@FeatureDocumentation(name = "Kodeordsløs funktionalitet", description = "Tillad brugen af stærke MFA klienter som fuld 2-faktor login, så kodeord ikke er nødvendigt")
	private boolean enablePasswordlessMfa = false;
	
	private String contactLocationForMails = "";
}
