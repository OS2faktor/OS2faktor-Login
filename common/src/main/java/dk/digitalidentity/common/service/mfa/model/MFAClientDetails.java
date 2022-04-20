package dk.digitalidentity.common.service.mfa.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
public class MFAClientDetails {
	private String deviceId;
	private Date created;
	private Date associatedUserTimestamp;
	private String pid;
}