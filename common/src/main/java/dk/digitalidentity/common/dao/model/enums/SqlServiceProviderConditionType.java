package dk.digitalidentity.common.dao.model.enums;

public enum SqlServiceProviderConditionType {
    DOMAIN("enum.sp.conditiontype.domain"),
    GROUP("enum.sp.conditiontype.group");

    public String message;

    private SqlServiceProviderConditionType(String message) {
        this.message = message;
    }
}
