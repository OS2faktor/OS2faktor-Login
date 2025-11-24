package dk.digitalidentity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.config.FeatureDocumentation;
import dk.digitalidentity.config.modules.ClaimsProviderConfiguration;
import dk.digitalidentity.config.modules.IdPKeystoreConfiguration;
import dk.digitalidentity.config.modules.OIDCConfiguration;
import dk.digitalidentity.config.modules.PasswordConfiguration;
import lombok.Getter;
import lombok.Setter;

@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "os2faktor.idp")
public class OS2faktorConfiguration {
	private IdPKeystoreConfiguration keystore = new IdPKeystoreConfiguration();
	private OIDCConfiguration oidc = new OIDCConfiguration();
	private PasswordConfiguration password = new PasswordConfiguration();
	private ClaimsProviderConfiguration claimsProvider = new ClaimsProviderConfiguration();
	private String entityId;
	private String baseUrl;

	// iFrameCspPolicy is only possible if XFrameOptions are disabled
	private boolean disableXFrameOptions = false;
	private String iFrameCspPolicy = null;
	
	// for debugging specific users - browser-window is not closed for these users during WCP login
	private String wcpRemainOpenForSamAccountName;
	
	@FeatureDocumentation(name = "UPN login", description = "Tillad at brugerne kan anvende UPN som brugernavn til login")
	private boolean loginWithUpn = false;

	// moved into database, this exists only for migration purposes (and local testing/bootstrapping)
	@Deprecated
	public IdPKeystoreConfiguration getKeystore() {
		return keystore;
	}
}
