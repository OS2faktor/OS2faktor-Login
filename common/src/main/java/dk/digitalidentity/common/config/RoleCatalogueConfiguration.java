package dk.digitalidentity.common.config;

import javax.annotation.PostConstruct;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.Getter;
import lombok.Setter;

@Component
@Setter
@Getter
public class RoleCatalogueConfiguration {
	private String baseUrl;
	private String apiKey;

	@FeatureDocumentation(name = "OS2rollekatalog integration", description = "Integration til OS2rollekatalog til opslag på rettigheder")
	private boolean enabled;

	@PostConstruct
	private void postConstruct() {
		this.enabled = (StringUtils.hasLength(baseUrl) && StringUtils.hasLength(apiKey));
		
		if (StringUtils.hasLength(baseUrl) && !baseUrl.endsWith("/")) {
			baseUrl = baseUrl + "/";
		}
	}
}
