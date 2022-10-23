package dk.digitalidentity.common.dao.model.enums;

import lombok.Getter;

@Getter
public enum SchoolRoleValue {
	
	// grownups
	TEACHER("Lærer"), 
	PEDAGOGUE("Pædagog"), 
	SUBSTITUTE("Vikar"), 
	LEADER("Leder"), 
	MANAGEMENT("Ledelse"),
	TAP("TAP"),
	CONSULTANT("Konsulent"),
	EXTERN("Ekstern"),
	TRAINEE("Praktikant"),
	
	// kids
	STUDENT("Elev");
	
	private String message;

    private SchoolRoleValue(String message) {
        this.message = message;
    }
}
