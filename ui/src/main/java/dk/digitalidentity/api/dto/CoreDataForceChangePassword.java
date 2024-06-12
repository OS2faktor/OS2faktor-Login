package dk.digitalidentity.api.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoreDataForceChangePassword {
	private String domain;
	private String samAccountName;
}
