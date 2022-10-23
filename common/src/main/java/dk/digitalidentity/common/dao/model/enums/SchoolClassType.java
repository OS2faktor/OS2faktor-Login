package dk.digitalidentity.common.dao.model.enums;

import lombok.Getter;

@Getter
public enum SchoolClassType {
    MAIN_GROUP("Hovedgruppe"), 
    YEAR("Årgang"),
    DIRECTION("Retning"),
    UNIT("Hold"),
    SFO("SFO"),
    TEAM("Team"),
    OTHER("Andet");
	
	private String message;

    private SchoolClassType(String message) {
        this.message = message;
    }
}
