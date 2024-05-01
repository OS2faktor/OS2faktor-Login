package dk.digitalidentity.api.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoreDataDeleteEntry {
	private String cpr;
	private String samAccountName;
}
