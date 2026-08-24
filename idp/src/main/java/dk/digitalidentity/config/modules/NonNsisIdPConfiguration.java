package dk.digitalidentity.config.modules;

import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@Setter
@Getter
public class NonNsisIdPConfiguration {
	private String name;
	private String entityId;
	private String metadata;
	private boolean passive = true;
}
