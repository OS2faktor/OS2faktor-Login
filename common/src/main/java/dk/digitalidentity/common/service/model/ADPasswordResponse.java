package dk.digitalidentity.common.service.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ADPasswordResponse {
	private boolean valid;
	private String message;
}
