package dk.digitalidentity.mvc.selfservice.dto;

import lombok.Getter;

@Getter
public enum SelfServiceStatus {
	NOT_ISSUED("enum.selfservice.status.notissued"),
	ACTIVE("enum.selfservice.status.active"),
	BLOCKED("enum.selfservice.status.blocked");

	private String message;

	private SelfServiceStatus(String message) {
		this.message = message;
	}
}
