package dk.digitalidentity.config.modules;

import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@Setter
@Getter
public class RoleCatalogueConfiguration {
	private String baseUrl;
	private String apiKey;
}
