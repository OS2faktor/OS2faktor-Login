package dk.digitalidentity.service.model.enums;

import lombok.Getter;

@Getter
public enum PasswordValidationResult {
	VALID(true),
	VALID_EXPIRED(true),
	VALID_BUT_BAD_PASWORD(true),
	INVALID(false),
	INVALID_BAD_PASSWORD(false),
	LOCKED(false),
	INSUFFICIENT_PERMISSION(false),
	TECHNICAL_ERROR(false);

	private boolean noErrors;

	private PasswordValidationResult(boolean noErrors) {
		this.noErrors = noErrors;
	}
}
