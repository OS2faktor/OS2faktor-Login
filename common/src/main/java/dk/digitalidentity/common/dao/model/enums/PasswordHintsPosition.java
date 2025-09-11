package dk.digitalidentity.common.dao.model.enums;

import lombok.Getter;

@Getter
public enum PasswordHintsPosition {

	ABOVE_COLLAPSED("enum.password.hints.position.above_collapsed"),
	ABOVE_EXPANDED("enum.password.hints.position.above_expanded"),
	BELOW_COLLAPSED("enum.password.hints.position.below_collapsed"),
	BELOW_EXPANDED("enum.password.hints.position.below_expanded");

	private String message;

	private PasswordHintsPosition(String message) {
		this.message = message;
	}

}
