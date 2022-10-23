package dk.digitalidentity.api.dto;

import java.util.Set;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoreDataNemLoginAllowed {
	private String domain;
	private Set<String> nemLoginUserUuids;
}
