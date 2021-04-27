package dk.digitalidentity.mvc.admin.dto;

import lombok.Getter;

@Getter
public enum IdentificationType {
	PASSPORT("enum.identification.passport"),
	DRIVERS_LICENSE("enum.identification.driverslicense"),
	OTHER("enum.identification.other");

	private String message;

	IdentificationType(String message) {
		this.message = message;
	}
}
