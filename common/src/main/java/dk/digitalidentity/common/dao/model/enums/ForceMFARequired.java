package dk.digitalidentity.common.dao.model.enums;

import lombok.Getter;

@Getter
public enum ForceMFARequired {
    ALWAYS("enum.forceMFARequired.always"),
    NEVER("enum.forceMFARequired.never"),
    DEPENDS("enum.forceMFARequired.depends");

    private String message;

    private ForceMFARequired(String message) {
        this.message = message;
    }
}