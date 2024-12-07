package dk.digitalidentity.nemlogin.service.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthenticatorsResponse {
	public List<Authenticator> authenticators;
}
