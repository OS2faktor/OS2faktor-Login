package dk.digitalidentity.config.modules;

import dk.digitalidentity.common.config.FeatureDocumentation;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class EBoks {
	
	@FeatureDocumentation(name = "Digital post", description = "Mulighed for at afsende digital post til medarbejdere i forbindelse med advisering, eller for at understøtte manuelle registreringsflows")
	private boolean enabled = false;

	private String senderName;
	private String url = "http://sf1601.digital-identity.dk/api/print";
	
	// set this if we need to send through another CVR
	private String overrideCvr = null;
}
