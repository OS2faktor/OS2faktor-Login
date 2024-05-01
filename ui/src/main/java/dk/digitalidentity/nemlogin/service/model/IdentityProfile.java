package dk.digitalidentity.nemlogin.service.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class IdentityProfile {
	private String status = "Pending"; // before was "active"
	private String registeredIal = "Substantial";
	private String targetIal = "Substantial";
	private String givenName;
	private String surname;
	private String emailAddress;
	private String cprNumber;
	private String rid;
}
