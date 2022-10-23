package dk.digitalidentity.common.config;

import java.util.Arrays;
import java.util.List;

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
	private String enabledClients = "WINDOWS,IOS,ANDROID,CHROME,YUBIKEY,EDGE";
	
	public List<String> getEnabledClients() {
		return Arrays.asList(enabledClients.split(","));
	}
}
