package dk.digitalidentity.common.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MfaDatabase {
	private boolean enabled = false;
	private String url;
	private String username;
	private String password;
	private String encryptionKey;
}
