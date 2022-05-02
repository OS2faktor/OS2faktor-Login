package dk.digitalidentity.common.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Cpr {
	
	@FeatureDocumentation(name = "CPR integration", description = "Integration til CPR registeret til opslag på status og ajourføring af navn")
	private boolean enabled = true;

	private String url = "http://cprservice.digital-identity.dk";
}
