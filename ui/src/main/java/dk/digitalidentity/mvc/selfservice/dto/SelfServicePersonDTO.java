package dk.digitalidentity.mvc.selfservice.dto;

import java.util.List;

import dk.digitalidentity.common.dao.model.SchoolRole;
import dk.digitalidentity.mvc.selfservice.NSISStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SelfServicePersonDTO {
	private String userId;
	private String email;
	private String name;
	private NSISStatus nsisStatus;
	private List<SchoolRole> schoolRoles;
}
