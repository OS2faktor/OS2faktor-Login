package dk.digitalidentity.common.dao.model.enums;

import lombok.Getter;

@Getter
public enum ForceMFARequired {
    ALWAYS("enum.forceMFARequired.always"),
    DEPENDS("enum.forceMFARequired.depends"),
    ONLY_FOR_UNKNOWN_NETWORKS("enum.forceMFARequired.unknownNetworks");

    private String message;

    private ForceMFARequired(String message) {
        this.message = message;
    }
}