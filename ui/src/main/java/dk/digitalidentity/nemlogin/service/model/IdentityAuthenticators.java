package dk.digitalidentity.nemlogin.service.model;

import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class IdentityAuthenticators {
	private List<String> authenticatorTypes = Arrays.asList("LocalIdentityProvider");
	private String subjectNameId;
}
