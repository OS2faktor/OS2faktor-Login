package dk.digitalidentity.controller.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PasswordChangeForm {
	private String password;
	private String confirmPassword;
}
