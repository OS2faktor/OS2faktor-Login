package dk.digitalidentity.api.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter 
public class CoreDataNemLoginEntry {
	private String cpr;
	private String samAccountName;
	private String nemloginUserUuid;
	private boolean active;
}
