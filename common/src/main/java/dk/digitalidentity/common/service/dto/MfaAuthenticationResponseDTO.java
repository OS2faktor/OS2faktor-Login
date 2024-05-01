package dk.digitalidentity.common.service.dto;

import dk.digitalidentity.common.service.mfa.model.MfaAuthenticationResponse;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MfaAuthenticationResponseDTO {
	private MfaAuthenticationResponse mfaAuthenticationResponse;
	private boolean success = false;
	private String failureMessage;
}
