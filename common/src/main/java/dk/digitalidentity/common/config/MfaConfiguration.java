package dk.digitalidentity.common.config;

import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@Getter
@Setter
public class MfaConfiguration {
	private String baseUrl = "https://backend.os2faktor.dk";
	private String apiKey;
	private String managementApiKey;

	@FeatureDocumentation(name = "TOTP klienter", description = "Tillad anvendelsen af TOTP 2-faktor enheder, fx Microsoft Authenticator")
	private boolean allowTotp = false;
}
