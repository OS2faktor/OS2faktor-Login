package dk.digitalidentity.nemlogin.service.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CredentialsRequest {
	private List<String> authenticatorSettingTypes = new ArrayList<>();
	private List<String> authenticatorTypesToShip = new ArrayList<>();
	private List<String> sharedCredentialUuids = new ArrayList<>();
}
