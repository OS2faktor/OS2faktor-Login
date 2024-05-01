package dk.digitalidentity.service.model.enums;

import lombok.Getter;

@Getter
public enum PasswordValidationResult {
	VALID(true),
	VALID_CACHE(true),
	VALID_EXPIRED(true),
	INVALID(false),
	LOCKED(false),
	INSUFFICIENT_PERMISSION(false),
	TECHNICAL_ERROR(false);

	private boolean noErrors;

	private PasswordValidationResult(boolean noErrors) {
		this.noErrors = noErrors;
	}
}
