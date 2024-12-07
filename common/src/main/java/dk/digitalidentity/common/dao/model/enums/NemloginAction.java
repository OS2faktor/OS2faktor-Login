package dk.digitalidentity.common.dao.model.enums;

import lombok.Getter;

@Getter
public enum NemloginAction {
	
	// day-to-day operations
	CREATE("Opret"),
	DELETE("Slet"),
	SUSPEND("Suspender"),
	REACTIVATE("Aktiver"),
	CHANGE_EMAIL("Opdater email"),
	UPDATE_PROFILE_ONLY("Opdater profil"),
	
	// when migrating, we use these three (either ACTIVE+ASSIGN or PROFILE+ACTIVE+ASSIGN, depending on initial state)
	UPDATE_PROFILE("Opdater profil"),
	ACTIVATE("Aktiver"),
	ASSIGN_LOCAL_USER_ID("Tilføj loginmiddel"),

	ASSIGN_PRIVATE_MIT_ID("Tilføj privat MitID"),
	REVOKE_PRIVATE_MIT_ID("Fjern privat MitID");
	
	private String message;
	
	private NemloginAction(String message) {
		this.message = message;
	}
}
