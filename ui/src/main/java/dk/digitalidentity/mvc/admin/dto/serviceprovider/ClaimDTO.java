package dk.digitalidentity.mvc.admin.dto.serviceprovider;

import dk.digitalidentity.common.dao.model.SqlServiceProviderRequiredField;
import dk.digitalidentity.common.dao.model.SqlServiceProviderRoleCatalogueClaim;
import dk.digitalidentity.common.dao.model.SqlServiceProviderStaticClaim;
import dk.digitalidentity.common.dao.model.enums.ClaimType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ClaimDTO {
	private long id;
	private ClaimType type;
	private String attribute;
	private String value;
	private String externalOperation;
	private String externalOperationArgument;

	public ClaimDTO(SqlServiceProviderRequiredField requiredField) {
		this.id = requiredField.getId();
		this.attribute = requiredField.getAttributeName();
		this.value = requiredField.getPersonField();

		this.type = ClaimType.DYNAMIC;
	}

	public ClaimDTO(SqlServiceProviderStaticClaim staticClaim) {
		this.id = staticClaim.getId();
		this.attribute = staticClaim.getField();
		this.value = staticClaim.getValue();

		this.type = ClaimType.STATIC;
	}

	public ClaimDTO(SqlServiceProviderRoleCatalogueClaim rcClaim) {
		this.id = rcClaim.getId();
		this.attribute = rcClaim.getClaimName();
		this.value = rcClaim.getClaimValue();
		this.externalOperation = rcClaim.getExternalOperation().toString();
		this.externalOperationArgument = rcClaim.getExternalOperationArgument();

		this.type = ClaimType.ROLE_CATALOGUE;
	}
}
