package dk.digitalidentity.config.modules;

import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@Setter
@Getter
public class IdPKeystoreConfiguration {
	// this is only ever used for bootstrapping - the certificate is loaded into the database, and then this setting
	// is never used again.
	private String location;
	private String password;
}
