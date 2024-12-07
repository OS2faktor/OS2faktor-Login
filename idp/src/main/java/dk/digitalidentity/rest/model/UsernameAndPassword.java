package dk.digitalidentity.rest.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UsernameAndPassword {
	private String username;
	private String password;
	private String previousToken;
	private String version;
	private boolean base64;
}
