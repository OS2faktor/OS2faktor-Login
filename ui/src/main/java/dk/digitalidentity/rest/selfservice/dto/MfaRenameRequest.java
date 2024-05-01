package dk.digitalidentity.rest.selfservice.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MfaRenameRequest {
	private String mfaDeviceId;
	private String mfaNewName;
}
