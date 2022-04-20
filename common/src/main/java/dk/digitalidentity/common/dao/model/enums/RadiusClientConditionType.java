package dk.digitalidentity.common.dao.model.enums;

public enum RadiusClientConditionType {
    DOMAIN("enum.radius.conditiontype.domain"),
    GROUP("enum.radius.conditiontype.group"),
    WITH_ATTRIBUTE("enum.radius.conditiontype.attribute");

    public String message;

    private RadiusClientConditionType(String message) {
        this.message = message;
    }
}
