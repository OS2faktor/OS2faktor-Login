package dk.digitalidentity.api.dto;

import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class PasswordRequest {

	@NotNull
	private String domain;
	
	@NotNull
	private String userId;
	
	@NotNull
	private String password;
}
