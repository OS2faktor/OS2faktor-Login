package dk.digitalidentity.common.service.model;

import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoleCatalogueRolesResponse {
	private String nameID;
	private List<String> userRoles;
	private List<String> systemRoles;
	private List<String> dataRoles;
	private List<String> functionRoles;
	private Map<String, String> roleMap;
}
