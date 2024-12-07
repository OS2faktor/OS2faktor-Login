package dk.digitalidentity.common.config.modules;

import dk.digitalidentity.common.config.FeatureDocumentation;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EntraMfaConfiguration {
	
	@FeatureDocumentation(name = "EntraID MFA integration", description = "Mulighed for at EntraID kan anvende OS2faktor MFA som en step-up mekanisme")
	private boolean enabled = false;

	// i princippet hardkodet, da den er ens i alle kommuner, men i en config så vi KAN ændre den hvis de pludselig ændrer den
	private String redirectUrl = "https://login.microsoftonline.com/common/federation/externalauthprovider";
}
