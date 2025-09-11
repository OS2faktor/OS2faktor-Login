package dk.digitalidentity.common.service.mfa.model;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MfaRenameRequestDTO {
	private String deviceId;
	private String name;
}
