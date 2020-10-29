package dk.digitalidentity.rest.admin.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ToggleAdminDTO {
	private String type;
	private boolean state;
}
