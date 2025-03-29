package dk.digitalidentity.config.modules;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

@Component
@Setter
@Getter
public class OIDCConfiguration {
	// string so replace works easier :)
	private String cleanupRefreshTokensAfterDays = "7";
}
