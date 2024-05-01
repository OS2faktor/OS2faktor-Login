package dk.digitalidentity.nemlogin.service.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmployeeChangeLocalUserIdRequest {
	private List<String> authenticatorSettingTypes = Collections.singletonList("LocalIdentityProvider");
	private List<String> authenticatorTypesToShip = new ArrayList<>();
	private List<String> sharedCredentialUuids = new ArrayList<>();
	private String subjectNameId;
}
