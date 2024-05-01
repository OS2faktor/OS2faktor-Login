package dk.digitalidentity.common.config.modules;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
public class AdConfiguration {
	private String baseUrl;
	private String apiKey;
	private String passwordSecret;
}
