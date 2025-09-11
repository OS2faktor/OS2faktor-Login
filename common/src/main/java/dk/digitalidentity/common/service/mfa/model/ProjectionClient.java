package dk.digitalidentity.common.service.mfa.model;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectionClient {
	private String name;
	private boolean passwordless;
	private String clientType;
	private String deviceId;
	private String serialnumber;
	private String nsisLevel;
	private String ssn;
	private LocalDateTime lastUsed;
	private LocalDateTime associatedUserTimestamp;
}
