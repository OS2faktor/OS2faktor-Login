package dk.digitalidentity.common.config;

import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@Setter
@Getter
public class KombitConfiguration {
	public enum ROLE_SOURCE { OS2ROLLEKATALOG, JFR_GROUPS };
	
	@FeatureDocumentation(name = "KOMBIT integration", description = "Integration til KOMBITs Context Handler (produktion)")
	private boolean enabled = false;

	private String metadataUrl = "https://adgangsstyring.stoettesystemerne.dk/runtime/saml2auth/metadata.idp";
	private String entityId = "https://saml.adgangsstyring.stoettesystemerne.dk";
	private String assuranceLevel = "3";
	private boolean encryptAssertion = true;
	
	private ROLE_SOURCE roleSource = ROLE_SOURCE.OS2ROLLEKATALOG;

	// a few municipalities have groups with dashes, and roles with underscores, and this will fix their issue
	private boolean convertDashToUnderscore = false;
}
