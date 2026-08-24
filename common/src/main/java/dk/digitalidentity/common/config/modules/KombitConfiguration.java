package dk.digitalidentity.common.config.modules;

import java.util.Map;

import org.springframework.stereotype.Component;

import dk.digitalidentity.common.config.FeatureDocumentation;
import lombok.Getter;
import lombok.Setter;

@Component
@Setter
@Getter
public class KombitConfiguration {
	
	@FeatureDocumentation(name = "KOMBIT integration", description = "Integration til KOMBITs Context Handler (produktion)")
	private boolean enabled = true;

	// new Context Handler (prod)
	private String metadataUrlv2 = "https://n2adgangsstyring.stoettesystemerne.dk/runtime/saml2auth/metadata.idp";
	private String entityIdv2 = "https://saml.n2adgangsstyring.stoettesystemerne.dk/runtime";

	// new Context Handler (test)
	private String metadataUrlTestv2 = "https://n2adgangsstyring.eksterntest-stoettesystemerne.dk/runtime/saml2auth/metadata.idp";
	private String entityIdTestv2 = "https://saml.n2adgangsstyring.eksterntest-stoettesystemerne.dk/runtime";

	private boolean encryptAssertion = true;

	// a few municipalities have groups with dashes, and roles with underscores, and this will fix their issue
	private boolean convertDashToUnderscore = false;
	
	// hackish - temporary quickfix for certain municipalities needed to send extra claims to KOMBIT
	private String kombitDomain;
	private Map<String, String> extraKombitClaims;

	private boolean onlyAllowLoginFromKnownNetworks;
}
