package dk.digitalidentity.service.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SetPassword {
	private PasswordProfile passwordProfile = new PasswordProfile();
}
