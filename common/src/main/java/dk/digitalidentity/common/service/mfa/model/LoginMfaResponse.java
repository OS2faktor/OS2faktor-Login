package dk.digitalidentity.common.service.mfa.model;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginMfaResponse {
	private String lastClientDeviceId;
	private long count;
	private List<ProjectionClient> clients;
}
