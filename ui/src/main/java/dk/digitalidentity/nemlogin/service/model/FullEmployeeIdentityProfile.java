package dk.digitalidentity.nemlogin.service.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class FullEmployeeIdentityProfile {
	private boolean allowQualifiedCertificateIssuance;
	private String rid;
	private String status;	
}
