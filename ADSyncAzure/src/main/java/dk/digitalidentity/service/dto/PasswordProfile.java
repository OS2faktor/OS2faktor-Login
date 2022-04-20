package dk.digitalidentity.service.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordProfile {
	private boolean forceChangePasswordNextSignIn = false;
	private String password;
}
