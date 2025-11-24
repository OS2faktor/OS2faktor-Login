package dk.digitalidentity.mvc.admin.dto.serviceprovider;

import java.util.Optional;

import dk.digitalidentity.common.dao.model.Group;
import dk.digitalidentity.common.dao.model.SqlServiceProviderAdvancedClaim;
import dk.digitalidentity.common.dao.model.SqlServiceProviderGroupClaim;
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
	private boolean singleValueOnly;
	private String externalOperation;
	private String externalOperationArgument;
	private long groupId;
	private boolean valuePrefix;
	private boolean removePrefix;

	// computed, just for showing in the UI
	private String parameter;
	
	public ClaimDTO(SqlServiceProviderRequiredField requiredField) {
		this.id = requiredField.getId();
		this.attribute = requiredField.getAttributeName();
		this.value = requiredField.getPersonField();
		this.singleValueOnly = requiredField.isSingleValueOnly();

		this.type = ClaimType.DYNAMIC;
	}

	public ClaimDTO(SqlServiceProviderStaticClaim staticClaim) {
		this.id = staticClaim.getId();
		this.attribute = staticClaim.getField();
		this.value = staticClaim.getValue();
		this.singleValueOnly = false;

		this.type = ClaimType.STATIC;
	}
	
	public ClaimDTO(SqlServiceProviderAdvancedClaim advancedClaim) {
		this.id = advancedClaim.getId();
		this.attribute = advancedClaim.getClaimName();
		this.value = advancedClaim.getClaimValue();
		this.singleValueOnly = advancedClaim.isSingleValueOnly();

		this.type = ClaimType.ADVANCED;
	}

	public ClaimDTO(SqlServiceProviderRoleCatalogueClaim rcClaim) {
		this.id = rcClaim.getId();
		this.attribute = rcClaim.getClaimName();
		this.value = rcClaim.getClaimValue();
		this.externalOperation = rcClaim.getExternalOperation().toString();
		this.externalOperationArgument = rcClaim.getExternalOperationArgument();
		this.singleValueOnly = rcClaim.isSingleValueOnly();

		// ui shown only
		this.parameter = rcClaim.getExternalOperation().getMessage();
		
		this.type = ClaimType.ROLE_CATALOGUE;
	}

	public ClaimDTO(SqlServiceProviderGroupClaim groupClaim) {
		this.id = groupClaim.getId();
		this.attribute = groupClaim.getClaimName();
		this.value = groupClaim.getClaimValue();
		this.groupId = Optional.ofNullable(groupClaim.getGroup()).map(Group::getId).orElse(0L);
		this.singleValueOnly = groupClaim.isSingleValueOnly();
		this.valuePrefix = groupClaim.isValuePrefix();
		this.removePrefix = groupClaim.isRemovePrefix();

		// ui shown only
		this.parameter = Optional.ofNullable(groupClaim.getGroup()).map(Group::getName).orElse(null);
		
		this.type = ClaimType.GROUP;
	}
}
