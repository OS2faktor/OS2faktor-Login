package dk.digitalidentity.common.service.model;

import java.util.List;

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
}
