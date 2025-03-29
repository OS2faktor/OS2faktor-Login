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
	
	@FeatureDocumentation(name = "Registrant funktionalitet", description = "Gør det muligt at tildele Registrant rollen til administratorer, så der kan foretages manuel identitetssikring")
	private boolean enableRegistrant = true;

	private boolean enableUnlockAccount = true;
	
	private boolean showRegisterMfaClient = false;

	@FeatureDocumentation(name = "Kodeordsløs funktionalitet", description = "Tillad brugen af stærke MFA klienter som fuld 2-faktor login, så kodeord ikke er nødvendigt")
	private boolean enablePasswordlessMfa = false;
	
	private String contactLocationForMails = "";
}
