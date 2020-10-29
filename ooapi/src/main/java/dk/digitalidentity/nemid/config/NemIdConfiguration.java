package dk.digitalidentity.nemid.config;

import javax.annotation.PostConstruct;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import dk.digitalidentity.ooapi.environment.Environments;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "nemid")
public class NemIdConfiguration {
	private Environments.Environment oces2Environment = Environments.Environment.valueOf("OCESII_DANID_ENV_PROD");
	private PidConfiguration pid = new PidConfiguration();
	private KeystoreConfiguration keystore = new KeystoreConfiguration();	
	private String serverUrlPrefix = "https://applet.danid.dk";
	private String serviceProviderId;
	private String origin = "";

	@PostConstruct
	public void init() {
		Environments.setEnvironments(oces2Environment);
	}
}
