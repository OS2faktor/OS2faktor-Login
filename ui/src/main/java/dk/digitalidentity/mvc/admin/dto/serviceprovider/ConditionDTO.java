package dk.digitalidentity.mvc.admin.dto.serviceprovider;

import dk.digitalidentity.common.dao.model.RadiusClientCondition;
import dk.digitalidentity.common.dao.model.SqlServiceProviderCondition;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ConditionDTO {
    private long id;
    private String name;

    public ConditionDTO(SqlServiceProviderCondition condition) {

        switch (condition.getType()) {
            case GROUP:
                this.id = condition.getGroup().getId();
                this.name = condition.getGroup().getName();
                break;
            case DOMAIN:
                this.id = condition.getDomain().getId();
                this.name = condition.getDomain().getName();
                break;
            default:
                this.name = "";
                break;
        }
    }

    public ConditionDTO(RadiusClientCondition condition) {
        switch (condition.getType()) {
            case GROUP:
                this.id = condition.getGroup().getId();
                this.name = condition.getGroup().getName();
                break;
            case DOMAIN:
                this.id = condition.getDomain().getId();
                this.name = condition.getDomain().getName();
                break;
            case WITH_ATTRIBUTE:
                this.id = condition.getId();
                this.name = "";
                break;
            default:
                this.name = "";
                break;
        }
    }
}
