package dk.digitalidentity.mvc.admin.dto;

import lombok.Getter;

@Getter
public enum IdentificationType {
	PASSPORT("enum.identification.passport"),
	DRIVERS_LICENSE("enum.identification.driverslicense"),
	RESIDENCE_CARD("enum.identification.residencecard"),
	OTHER("enum.identification.other");

	private String message;

	IdentificationType(String message) {
		this.message = message;
	}
}
