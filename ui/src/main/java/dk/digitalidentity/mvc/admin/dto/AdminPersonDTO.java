package dk.digitalidentity.mvc.admin.dto;

import java.util.Map;

import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.mvc.selfservice.dto.SelfServiceStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminPersonDTO {
	private Long personId;
	private String userId;
	private SelfServiceStatus status;
	private String statusMessage;
	private NSISLevel nsisLevel;
	private String email;
	private Map<String, String> attributes;
	private String name;
	private String alias;
	private boolean nameProtected;
}