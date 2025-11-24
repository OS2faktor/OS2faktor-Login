package dk.digitalidentity.common.config.modules.nemlogin;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NemLoginSPConfiguration {
	private boolean enabled = false;
	private boolean encryptAssertion = true;
	private String orgName = "NotSet";
	
	private String entityId = "https://saml.nemlog-in.dk";
	private String metadataUrl = "https://www.digital-identity.dk/metadata/nemlogin3-prod-idp-metadata.xml";

	private boolean onlyAllowLoginFromKnownNetworks;
}
