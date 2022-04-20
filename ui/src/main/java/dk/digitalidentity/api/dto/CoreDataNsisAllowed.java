package dk.digitalidentity.api.dto;

import java.util.Set;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoreDataNsisAllowed {
	private String domain;
	private Set<String> nsisUserUuids;
}
