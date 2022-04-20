package dk.digitalidentity.mvc.selfservice.dto;

import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SelfServicePersonDTO {
	private String userId;
	private SelfServiceStatus status;
	private String statusMessage;
	private NSISLevel nsisLevel;
	private String email;
	private String name;
}
