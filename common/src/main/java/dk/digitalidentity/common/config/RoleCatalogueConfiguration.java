package dk.digitalidentity.common.config;

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
	
	public boolean isEnabled() {
		return (StringUtils.hasLength(baseUrl) && StringUtils.hasLength(apiKey));
	}
}
