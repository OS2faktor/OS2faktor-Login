package dk.digitalidentity.nemid.config;

import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@Setter
@Getter
public class KeystoreConfiguration {
	private String location;
	private String password;
}
