package dk.digitalidentity.api.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class KeystorePayload {
	private String alias;
	
	// option 1: this is available
	private String certificate;
	private String kmsAlias;
	
	// option 2: this is available
	private String keystore;
	private String password;
}
