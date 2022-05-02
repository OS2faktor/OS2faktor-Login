package dk.digitalidentity.config.modules;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class GeoLocate {
	private boolean enabled = false;
	private String url = "http://geolocate.digital-identity.dk/";	
}
