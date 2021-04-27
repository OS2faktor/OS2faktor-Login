package dk.digitalidentity.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "os2faktor.common")
public class CommonConfiguration {
	private MfaConfiguration mfa = new MfaConfiguration();
	private DevConfiguration dev = new DevConfiguration();
	private AdConfiguration ad = new AdConfiguration();
	private MailConfiguration email = new MailConfiguration();
	private CustomerConfiguration customer = new CustomerConfiguration();
}
