package dk.digitalidentity.config.modules;

import dk.digitalidentity.common.config.FeatureDocumentation;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class EBoks {
	
	@FeatureDocumentation(name = "Digital post", description = "Mulighed for at afsende digital post til medarbejdere for at understøtte manuelle registreringsflows")
	private boolean enabled = false;

	private String materialeId;
	private String senderId;
	private String url = "http://remoteprint.digital-identity.dk/";	
}
