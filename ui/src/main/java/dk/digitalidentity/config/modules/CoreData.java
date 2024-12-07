package dk.digitalidentity.config.modules;

import dk.digitalidentity.common.config.FeatureDocumentation;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoreData {
	private String apiKey;
	
	@FeatureDocumentation(name = "Ekstern rollestyring", description = "Mulighed for at administrere hvem der har administrator roller tildelt i OS2faktor via API kald")
	private boolean roleApiEnabled = false;
	
	@FeatureDocumentation(name = "Betroede medarbejdere", description = "Mulighed for at opmærke medarbejdere som betroede, så der gælder strammere kodeordspolitikker for dem end for andre brugere")
	private boolean trustedEmployeeEnabled = false;

}
