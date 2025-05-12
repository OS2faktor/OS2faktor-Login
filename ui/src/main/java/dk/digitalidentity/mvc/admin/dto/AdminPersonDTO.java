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
	private String status = "Unknown";
	private boolean mitidPrivatCredential;
	private boolean qualifiedSignature;
	private String mitidErhvervRid;

	private Long personId;
	private boolean nameProtected;
	private Map<String, String> attributes;
	private Map<String, String> kombitAttributes;
	private String nemloginUserUuid;
	private List<SchoolRole> schoolRoles;
	
	private boolean robot = false;
}