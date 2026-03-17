package dk.digitalidentity.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "os2faktor.websockets")
public class OS2faktorConfiguration {
	private String apiKey;
	private String webSocketKey;
	private long maxWait = 40;
	private Map<String,String> domainMap = new HashMap<>();
	
	private AzureProxyConfig azureProxy = new AzureProxyConfig();

	@EventListener(ApplicationReadyEvent.class)
	public void runOnStartup() {
		if (domainMap != null && domainMap.size() > 0) {
			for (String key : domainMap.keySet()) {
				log.info("Mapped domain '" + key + "' to '" + domainMap.get(key) + "'");
			}
		}
	}
}
