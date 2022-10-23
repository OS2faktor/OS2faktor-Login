package dk.digitalidentity.api.dto;

import java.util.List;

import dk.digitalidentity.common.dao.model.enums.SchoolRoleValue;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class StilPerson {
	private String cpr;
	private String institutionNumber;
	private String institutionName;
	private StilPersonType type;
	private SchoolRoleValue role;
	private String level;
	private List<String> groups;
}
