package dk.digitalidentity.mvc.admin.dto;

import dk.digitalidentity.common.dao.model.RadiusClientClaim;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RadiusClaimDTO {
    private String personField;
    private long attributeId;

    public RadiusClaimDTO(RadiusClientClaim claim) {
    	this.personField = claim.getPersonField();
    	this.attributeId = claim.getAttributeId();
    }
}
