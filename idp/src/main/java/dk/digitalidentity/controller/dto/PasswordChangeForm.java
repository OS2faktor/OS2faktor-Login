package dk.digitalidentity.controller.dto;

import java.io.Serializable;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PasswordChangeForm implements Serializable {
	private static final long serialVersionUID = -286544720952319815L;

	private String password;
	private String confirmPassword;
}
