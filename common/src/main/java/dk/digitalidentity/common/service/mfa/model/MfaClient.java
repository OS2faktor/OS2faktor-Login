package dk.digitalidentity.common.service.mfa.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MfaClient implements Serializable {
	private static final long serialVersionUID = 4935443945438852845L;

	private String name;
	private String deviceId;
	private ClientType type;
	private boolean hasPincode;
	private NSISLevel nsisLevel;
	private boolean prime;
	private boolean roaming;
	private boolean localClient = false;
	private boolean locked;
	private boolean passwordless;
	private boolean supportsChallengeFlow;
	private Date lockedUntil;
	private String serialnumber;

	// used for UI
	private transient String typeMessage;

	// used for mapping during night-batch
	private transient String ssn;

	// used for personStatistics
	private LocalDateTime lastUsed;

	private LocalDateTime associatedUserTimestamp;
}
