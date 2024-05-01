package dk.digitalidentity.nemlogin.service.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmployeeCreateRequest {
	private IdentityProfile identityProfile;
	private IdentityOrganizationProfile identityOrganizationProfile;
	private IdentityAuthenticators identityAuthenticators;
}
