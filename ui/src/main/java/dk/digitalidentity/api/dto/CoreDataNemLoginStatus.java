package dk.digitalidentity.api.dto;

import java.util.Set;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoreDataNemLoginStatus {
	private String domain;
	private Set<CoreDataNemLoginEntry> entries;
}
