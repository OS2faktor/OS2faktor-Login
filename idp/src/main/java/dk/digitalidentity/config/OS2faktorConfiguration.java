package dk.digitalidentity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import dk.digitalidentity.config.modules.IdPKeystoreConfiguration;
import dk.digitalidentity.config.modules.PasswordConfiguration;
import lombok.Getter;
import lombok.Setter;

@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "os2faktor.idp")
public class OS2faktorConfiguration {
	private IdPKeystoreConfiguration keystore = new IdPKeystoreConfiguration();
	private PasswordConfiguration password = new PasswordConfiguration();
	private String entityId;
	private String baseUrl;
	
	// allow users to login using only username/password when no MFA
	// registered and the user has nsis_allowed = false. Used when allowing
	// the registration of the first MFA device using self-service
	private boolean allowUsernamePasswordLoginIfNoMfa = false;
	
	// TODO: flip these around in the future, so default is MitID instead of NemID
	private boolean nemIdEnabled = true;
	private boolean mitIdEnabled = false;
}
