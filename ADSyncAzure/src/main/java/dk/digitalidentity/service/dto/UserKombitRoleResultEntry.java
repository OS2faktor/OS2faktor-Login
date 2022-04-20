package dk.digitalidentity.service.dto;

import java.util.HashSet;
import java.util.Set;

import dk.digitalidentity.service.dto.KombitJfr.Jfr;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserKombitRoleResultEntry {
	private Set<Jfr> addRoles;
	private Set<Jfr> removeRoles;

	public UserKombitRoleResultEntry(Set<Jfr> addRoles, Set<Jfr> removeRoles) {
		this.addRoles = addRoles;
		this.removeRoles = removeRoles;
	}

	public UserKombitRoleResultEntry() {
		this.addRoles = new HashSet<>();
		this.removeRoles = new HashSet<>();
	}
}
