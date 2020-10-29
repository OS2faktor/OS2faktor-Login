package dk.digitalidentity.config.modules;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoreData {
	private boolean enabled;
	private String domain;
	private boolean cprLookup;
	private String apiKey;
}
