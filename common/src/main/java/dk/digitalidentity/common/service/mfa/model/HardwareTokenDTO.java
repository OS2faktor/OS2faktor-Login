package dk.digitalidentity.common.service.mfa.model;

import dk.digitalidentity.common.service.enums.RegistrationStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HardwareTokenDTO {
	private RegistrationStatus status;
	private boolean found;
}
