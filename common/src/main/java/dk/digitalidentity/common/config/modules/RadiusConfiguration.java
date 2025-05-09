package dk.digitalidentity.common.config.modules;

import org.springframework.stereotype.Component;

import dk.digitalidentity.common.config.FeatureDocumentation;
import lombok.Getter;
import lombok.Setter;

@Component
@Getter
@Setter
public class RadiusConfiguration {
	
	@FeatureDocumentation(name = "RADIUS klienter", description = "Tillad at RADIUS klienter kan autentificere brugere op mod OS2faktor")
	private boolean enabled = false;

	private int port = 1812;
	private int mfaPort = 1813;
}
