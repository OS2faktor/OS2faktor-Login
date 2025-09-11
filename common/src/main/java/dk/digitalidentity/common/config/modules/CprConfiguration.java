package dk.digitalidentity.common.config.modules;

import dk.digitalidentity.common.config.FeatureDocumentation;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CprConfiguration {
	
	@FeatureDocumentation(name = "CPR integration", description = "Integration til CPR registeret til opslag på status og ajourføring af navn")
	private boolean enabled = true;

	// for now, this makes sense for private customers
	private boolean disablePeriodicLookup = false;

	private String url = "http://cprservice5.digital-identity.dk";
}
