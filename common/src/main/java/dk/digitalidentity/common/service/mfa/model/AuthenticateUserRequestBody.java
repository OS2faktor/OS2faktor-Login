package dk.digitalidentity.common.service.mfa.model;

import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuthenticateUserRequestBody {
	private String cpr;
	private NSISLevel nsisLevel;
}
