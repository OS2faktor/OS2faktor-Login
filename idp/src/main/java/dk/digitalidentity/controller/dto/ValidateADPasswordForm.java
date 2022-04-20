package dk.digitalidentity.controller.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ValidateADPasswordForm {
	private String password;
	private boolean dedicatedActivationFlow;
}
