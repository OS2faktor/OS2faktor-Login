package dk.digitalidentity.common.config.modules;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MfaDatabaseConfiguration {
	private boolean enabled = false;
	private String url;
	private String username;
	private String password;
	private String encryptionKey;
}
