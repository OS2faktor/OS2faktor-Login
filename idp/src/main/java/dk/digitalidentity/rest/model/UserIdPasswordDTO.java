package dk.digitalidentity.rest.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserIdPasswordDTO {
	private long userId;
	private String password;
}
