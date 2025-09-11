package dk.digitalidentity.common.config.modules;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class GeoLocate {
	private boolean enabled = true;
	private String url = "http://geolocate.digital-identity.dk/";	
}
