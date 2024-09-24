package dk.digitalidentity.common.config.modules;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import dk.digitalidentity.common.config.FeatureDocumentation;
import lombok.Setter;

@Component
@Setter
public class RoleCatalogueConfiguration {
	private String baseUrl;
	private String apiKey;

	@FeatureDocumentation(name = "OS2rollekatalog integration", description = "Integration til OS2rollekatalog til opslag på rettigheder")
	private boolean enabled;
	
	// default enabled - we have some who needs to have this disabled when they are onboarding OS2rollekatalog,
	// as they have not imported KOMBIT yet
	private boolean kombitRolesEnabled = true;
	
	public boolean isEnabled() {
		return (enabled && StringUtils.hasLength(baseUrl) && StringUtils.hasLength(apiKey));
	}
	
	public String getBaseUrl() {
		if (StringUtils.hasLength(baseUrl) && !baseUrl.endsWith("/")) {
			return baseUrl + "/";
		}

		return baseUrl;
	}
	
	public String getApiKey() {
		return apiKey;
	}
	
	public boolean isKombitRolesEnabled() {
		return kombitRolesEnabled;
	}
}
