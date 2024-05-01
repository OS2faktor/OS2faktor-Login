package dk.digitalidentity.common.service.mfa.model;

import java.util.Date;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MFAClientDetails {
	private String deviceId;
	private Date created;
	private Date associatedUserTimestamp;
	private String pid;
	private boolean locked;
	private Date lockedUntil;
}