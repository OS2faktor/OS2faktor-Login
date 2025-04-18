package dk.digitalidentity.api.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordResponse {
	public enum PasswordStatus { OK, FAILURE, TECHNICAL_ERROR, TIMEOUT, INSUFFICIENT_PERMISSION }
	
	private PasswordStatus status;
	private String message;
}
