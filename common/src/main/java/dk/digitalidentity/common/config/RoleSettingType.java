package dk.digitalidentity.common.config;

import lombok.Getter;

@Getter
public enum RoleSettingType {
	CAN_CHANGE_PASSWORD_ON_GROUP_MATCH("Må skifte kodeord på elever, som er tilknyttet samme grupper som en selv, men afgrænset til udvalgte gruppetyper"),
	CAN_CHANGE_PASSWORD_ON_LEVEL_MATCH("Må skifte kodeord på elever i udvalgte klassetrin."),
	CANNOT_CHANGE_PASSWORD("Må ikke skifte kodeord på elever");
	
	private String message;

    private RoleSettingType(String message) {
        this.message = message;
    }
}
