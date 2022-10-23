package dk.digitalidentity.mvc.admin.dto;

import java.util.List;
import java.util.Map;

import dk.digitalidentity.common.dao.model.SchoolRole;
import dk.digitalidentity.mvc.selfservice.NSISStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminPersonDTO {
	private String userId;
	private String email;
	private String name;
	private NSISStatus nsisStatus;

	private Long personId;
	private boolean nameProtected;
	private Map<String, String> attributes;
	private List<SchoolRole> schoolRoles;
}