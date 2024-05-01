package dk.digitalidentity.rest.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UsernameAndPasswordChange {
	private String username;
	private String oldPassword;
	private String newPassword;
	private String version;
}
