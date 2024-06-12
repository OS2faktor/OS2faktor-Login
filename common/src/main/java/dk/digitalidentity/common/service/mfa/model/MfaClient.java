package dk.digitalidentity.common.service.mfa.model;

import java.io.Serializable;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Date;

import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
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
	private Date lockedUntil;
	private String serialnumber;

	// used for UI
	private transient String typeMessage;

	// used for mapping during night-batch
	private transient String ssn;

	// used for personStatistics
	private LocalDateTime lastUsed;

	private LocalDateTime associatedUserTimestamp;

	public MfaClient(String name, String deviceId, String serialnumber, String type, String nsisLevel) {
		this.name = name;
		this.deviceId = deviceId;
		this.type = ClientType.valueOf(type);
		this.nsisLevel = (nsisLevel != null) ? NSISLevel.valueOf(nsisLevel) : NSISLevel.NONE;
		this.serialnumber = serialnumber;
	}
	
	public MfaClient(String name, String deviceId, String serialnumber, String type, String nsisLevel, String ssn, Timestamp lastUsed, Timestamp associatedUserTimestamp) {
		this.name = name;
		this.deviceId = deviceId;
		this.type = ClientType.valueOf(type);
		this.nsisLevel = (nsisLevel != null) ? NSISLevel.valueOf(nsisLevel) : NSISLevel.NONE;
		this.ssn = ssn;
		this.serialnumber = serialnumber;

		if (lastUsed != null) {
			this.lastUsed = lastUsed.toLocalDateTime();
		}

		if (associatedUserTimestamp != null) {
			this.associatedUserTimestamp = associatedUserTimestamp.toLocalDateTime();
		}
	}
}
