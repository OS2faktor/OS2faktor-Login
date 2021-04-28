package dk.digitalidentity.api.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordResponse {
	private boolean valid;
	private String message;
}
