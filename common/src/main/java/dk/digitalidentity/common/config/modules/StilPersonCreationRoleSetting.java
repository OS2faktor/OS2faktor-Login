package dk.digitalidentity.common.config.modules;

import dk.digitalidentity.common.dao.model.enums.SchoolRoleValue;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StilPersonCreationRoleSetting {
	private SchoolRoleValue role;
	private boolean nsisAllowed;
	private boolean transferToNemLogin;
}
