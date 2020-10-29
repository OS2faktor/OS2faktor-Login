package dk.digitalidentity.common.service.mfa.model;

import java.io.Serializable;

import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MfaClient implements Serializable {
	private static final long serialVersionUID = 4935443945438852845L;

	private String name;
	private String deviceId;
	private ClientType type;
	private boolean hasPincode;
	private NSISLevel nsisLevel;
}
