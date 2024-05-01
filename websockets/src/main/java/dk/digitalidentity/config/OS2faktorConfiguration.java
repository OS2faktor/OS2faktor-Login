package dk.digitalidentity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "os2faktor.websockets")
public class OS2faktorConfiguration {
	private String apiKey;
	private String webSocketKey;
	private long maxWait = 40;
}
