package dk.digitalidentity.api.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForceChangePwd {
	private String samAccountName;
	private String domain;
}
