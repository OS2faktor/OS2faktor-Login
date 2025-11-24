package dk.digitalidentity.config.modules;

import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@Setter
@Getter
public class ClaimsProviderConfiguration {
	private String mitIdEntityId = "https://saml.nemlog-in.dk";
	private String mitIdMetadata = "https://www.digital-identity.dk/metadata/nemlogin3-prod-metadata.xml";
	private boolean mitIdEnabed = true;
	
	private String stilEntityId = "https://broker.unilogin.dk/auth/realms/broker";
	private String stilMetadata = "https://www.digital-identity.dk/metadata/stil-metadata-for-sp.xml";
	private boolean stilEnabled = false;
}
