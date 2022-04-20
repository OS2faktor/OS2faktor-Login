package dk.digitalidentity.common.config;

import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@Setter
@Getter
public class StilConfiguration {
	private boolean enabled = false;
	private String metadataLocation = "/cert/unilogin_metadata.xml";
	private String entityId = "https://broker.unilogin.dk/auth/realms/broker";
	private String uniloginAttribute = "cpr";
	private boolean encryptAssertion = true;
}
