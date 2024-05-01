package dk.digitalidentity.common.config.modules.school;

import dk.digitalidentity.common.dao.model.enums.RoleSettingType;
import dk.digitalidentity.common.dao.model.enums.SchoolRoleValue;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StudentPwdRoleSettingConfiguration {
	private SchoolRoleValue role;
	private RoleSettingType type;
	private String filter;
}
