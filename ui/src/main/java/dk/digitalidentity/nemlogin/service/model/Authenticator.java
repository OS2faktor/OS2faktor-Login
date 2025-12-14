package dk.digitalidentity.nemlogin.service.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class Authenticator {
	// Many more fields, but these are the ones we care about
	private String uuid;
	private String type;
	private String status;
	private String id;
}
