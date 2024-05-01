package dk.digitalidentity.common.config.modules;

import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@Setter
@Getter
public class SelfServiceConfiguration {
	private String baseUrl;
	private String metadataUrl;
	private String entityId;
	private boolean encryptAssertion = true;
}
