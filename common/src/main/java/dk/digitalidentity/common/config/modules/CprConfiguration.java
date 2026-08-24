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
	
	// switch this to default true (or just remove it) when we move to the datafordeler for everyone. For now we can enable on a customer-by-customer basis
	private boolean useDatafordeler = false;

	private String url = "http://cprservice5.digital-identity.dk";
}
