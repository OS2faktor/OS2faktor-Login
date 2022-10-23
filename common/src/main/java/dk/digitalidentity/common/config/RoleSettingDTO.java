package dk.digitalidentity.common.config;

import dk.digitalidentity.common.dao.model.enums.SchoolRoleValue;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoleSettingDTO {
	private SchoolRoleValue role;
	private RoleSettingType type;
	private String filter;
}
