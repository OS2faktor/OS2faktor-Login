package dk.digitalidentity.service.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoreDataDeleteEntry {
	private String uuid;
	private String cpr;
	private String samAccountName;
}
