package dk.digitalidentity.common.config.modules;

import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@Getter
@Setter
public class DevConfiguration {
	private boolean enabled;
}
