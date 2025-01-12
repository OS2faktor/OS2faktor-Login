package dk.digitalidentity.rest.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordFilterValidationDTO {
	private String accountName;
	private String password;
}
