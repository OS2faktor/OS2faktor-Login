package dk.digitalidentity.mvc.admin.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.service.mfa.model.ClientType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ActivationDTO {
	private long personId;
	
	@NotNull
	private IdentificationType identificationType;
	
	@NotNull
	@Min(value = 2)
	private String identification;

	private String note;
	
	@NotNull
	private NSISLevel nsisLevel;
	
	// for Manuel MFA Client registation only
	private String name;
	private ClientType type;
	private String deviceId;
}
