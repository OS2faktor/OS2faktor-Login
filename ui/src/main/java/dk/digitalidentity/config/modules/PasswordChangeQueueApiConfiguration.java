package dk.digitalidentity.config.modules;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordChangeQueueApiConfiguration {
	private String apiKey;
	private boolean enabled = false;
}
