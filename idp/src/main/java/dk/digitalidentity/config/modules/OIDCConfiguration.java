package dk.digitalidentity.config.modules;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

@Component
@Setter
@Getter
public class OIDCConfiguration {
	private String enabled;
}
