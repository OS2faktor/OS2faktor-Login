package dk.digitalidentity.config.modules;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MitID {
	// not needed for municipalities, but for private companies, we should use the cached value
	public boolean allowCachedUuidLookup = false;
}
