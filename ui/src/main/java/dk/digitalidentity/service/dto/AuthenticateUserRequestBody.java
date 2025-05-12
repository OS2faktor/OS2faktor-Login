package dk.digitalidentity.service.dto;

import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuthenticateUserRequestBody {
	private String cpr;
	private NSISLevel nsisLevel;
}
