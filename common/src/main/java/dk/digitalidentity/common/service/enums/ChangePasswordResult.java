package dk.digitalidentity.common.service.enums;

import lombok.Getter;

@Getter
public enum ChangePasswordResult {

	// all is well, password changes
	OK(""),

	// technical errors
	TECHNICAL_MISSING_PERSON("Kan ikke finde en brugerkonto at skifte kodeord på"),
	
	// validation errors
	TOO_SHORT("Det angivne kodeord er for kort"),
	TOO_LONG("Det angivne kodeord er for langt"),
	NOT_COMPLEX("Det angivne kodeord opfylder ikke reglerne for et komplekst kodeord"),
	NO_LOWERCASE("Det angivne kodeord indeholder ikke mindst ét lille bogstaver"),
	NO_UPPERCASE("Det angivne kodeord indeholder ikke mindst ét stort bogstaver"),
	NO_DIGITS("Det angivne kodeord indeholder ikke mindst ét tal"),
	NO_SPECIAL_CHARACTERS("Det angivne kodeord indeholder ikke mindst ét specialtegn"),
	DANISH_CHARACTERS_NOT_ALLOWED("Det angivne kodeord indeholder mindst ét dansk tegn"),
	CONTAINS_NAME("Det angivne kodeord indeholder brugerens navn eller brugernavn"),
	OLD_PASSWORD("Det angivne kodeord er det samme som et gammelt kodeord"),
	BAD_PASSWORD("Det valgte kodeord indeholder et forbudt ord, og kan ikke anvendes"),
	WRONG_SPECIAL_CHARACTERS("Det valgte kodeord indeholder specialtegn som ikke er tilladt"),
	LEAKED_PASSWORD("Det kodeord du har valgt findes i kendte læk af kodeord, vælg venligst et andet");

	private String message;

	private ChangePasswordResult(String message) {
		this.message = message;
	}
}
