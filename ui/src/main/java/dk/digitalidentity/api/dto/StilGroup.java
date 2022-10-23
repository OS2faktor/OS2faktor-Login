package dk.digitalidentity.api.dto;

import dk.digitalidentity.common.dao.model.enums.SchoolClassType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StilGroup {
	private String id;
	private SchoolClassType type;
	private String institutionNumber;
	private String level;
	private String name;
}
