package dk.digitalidentity.service.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MfaRenameRequestDTO {
	private String deviceId;
	private String name;
}
