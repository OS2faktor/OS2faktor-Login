package dk.digitalidentity.common.service.geo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GeoIP {
	private String ip;
	private String city;
	private String country;
	private boolean retry;
}