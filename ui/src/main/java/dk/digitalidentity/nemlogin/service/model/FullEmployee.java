package dk.digitalidentity.nemlogin.service.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class FullEmployee {
	private String uuid;
	
	private FullEmployeeIdentityProfile identityProfile;
	private IdentityPermissions identityPermissions;
}
