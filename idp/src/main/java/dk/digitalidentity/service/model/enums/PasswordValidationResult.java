package dk.digitalidentity.service.model.enums;

import lombok.Getter;

@Getter
public enum PasswordValidationResult {
	VALID(true),
	INVALID(false),
	VALID_EXPIRED(false);

	private boolean noErrors;

	private PasswordValidationResult(boolean noErrors) {
		this.noErrors = noErrors;
	}
}
