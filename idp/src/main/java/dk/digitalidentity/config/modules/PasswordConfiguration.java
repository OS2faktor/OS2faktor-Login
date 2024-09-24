package dk.digitalidentity.config.modules;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

@Component
@Setter
@Getter
public class PasswordConfiguration {
	
	// key for encrypting passwords in memory on session
	private String secret;
}
