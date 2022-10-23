package dk.digitalidentity.config.modules;

import dk.digitalidentity.common.config.FeatureDocumentation;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoreData {
	
	@FeatureDocumentation(name = "Bruger-associering", description = "Hvis denne funktion er slået til, er det muligt for administratorer at oprette associeringer til CPR numre direkte fra administrator-portalen i det brugerdomæne der hedder \"OS2faktor\".")
	private boolean enabled;

	private String apiKey;
}
