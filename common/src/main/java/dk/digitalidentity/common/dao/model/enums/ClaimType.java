package dk.digitalidentity.common.dao.model.enums;

import lombok.Getter;

@Getter
public enum ClaimType {
    DYNAMIC("Personligt"),
    STATIC("Fast"),
    ROLE_CATALOGUE("OS2rollekatalog"),
    ADVANCED("Avanceret"),
    GROUP("Gruppe");

    private String message;

    private ClaimType(String message) {
        this.message = message;
    }
}
