package dk.digitalidentity.config;

import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@Getter
@Setter
public class AzureProxyConfig {
	private boolean enabled;
	private String domain;
	private String url;
}
