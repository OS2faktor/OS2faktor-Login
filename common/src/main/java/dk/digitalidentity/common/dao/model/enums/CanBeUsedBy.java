package dk.digitalidentity.common.dao.model.enums;

import lombok.Getter;

@Getter
public enum CanBeUsedBy {
    ALL("Alle"),
    FROM_DOMAIN("Fra domæne"),
    WITH_ATTRIBUTE("Med attribut");

    private String message;

    private CanBeUsedBy(String message) {
        this.message = message;
    }
}
