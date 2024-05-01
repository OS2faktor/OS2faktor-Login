package dk.digitalidentity.config.modules;

import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@Setter
@Getter
public class IdPKeystoreConfiguration {
	private String location;
	private String password;
	
	// temporary code until NSIS-533 is merged
	private String secondaryLocation;
	private String secondaryPassword;
}
