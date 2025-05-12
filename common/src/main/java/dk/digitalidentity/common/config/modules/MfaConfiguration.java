package dk.digitalidentity.common.config.modules;

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
	private List<String> enabledClientsComputedValue = null;
	
	public List<String> getEnabledClients() {
		if (enabledClientsComputedValue == null) {
			enabledClientsComputedValue = Arrays.asList(enabledClients.trim().split(","));
		}
		
		return enabledClientsComputedValue;
	}
}
