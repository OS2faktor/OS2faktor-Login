package dk.digitalidentity.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UnlockRequest {

	@NotNull
	private String domain;

	@NotNull
	private String userName;
}
