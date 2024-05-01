package dk.digitalidentity.config.modules;

import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CertManagerApi {
	private boolean enabled = true;
	private String apiKey = UUID.randomUUID().toString();
}
