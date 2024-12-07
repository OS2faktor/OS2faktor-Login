package dk.digitalidentity.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class PasswordRequest {

	@NotNull
	private String domain;
	
	@NotNull
	private String userUuid;

	@NotNull
	private String userName;

	@NotNull
	private String password;
}
