package dk.digitalidentity.common.dao.model.enums;

import lombok.Getter;

@Getter
public enum RoleCatalogueOperation {
	GET_SYSTEM_ROLES("Alle tildelte systemroller"),
	GET_USER_ROLES("Alle tildelte jobfunktionsroller"),
	GET_SYSTEM_ROLES_OIOBPP("Alle tildelte systemroller (som OIO-BPP)"),
	CONDITION_MEMBER_OF_SYSTEM_ROLE("Hvis bruger tildelt systemrolle"),
	CONDITION_MEMBER_OF_USER_ROLE("Hvis bruger tildelt jobfunktionsrolle");

	private String message;

	private RoleCatalogueOperation(String message) {
		this.message = message;
	}
}
