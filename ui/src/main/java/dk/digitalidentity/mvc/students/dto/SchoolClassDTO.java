package dk.digitalidentity.mvc.students.dto;

import dk.digitalidentity.common.dao.model.SchoolClass;
import dk.digitalidentity.common.dao.model.enums.SchoolClassType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SchoolClassDTO {
	private long id;
	private String name;
	private String institutionId;
	private String classIdentifier;
	private String level;
	private SchoolClassType type;
	private String institutionName; // school name
	private boolean bulkChangeAllowed;

	public SchoolClassDTO(SchoolClass clazz, boolean bulkChangePasswordAllowed) {
		this.id = clazz.getId();
		this.name = clazz.getName();
		this.institutionId = clazz.getInstitutionId();
		this.classIdentifier = clazz.getClassIdentifier();
		this.level = clazz.getLevel();
		this.type = clazz.getType();
		this.bulkChangeAllowed = bulkChangePasswordAllowed;

		clazz.getRoleMappings().stream()
					    .map(mapping -> mapping.getSchoolRole().getInstitutionName())
					    .findFirst()
					    .ifPresent(x -> this.institutionName = x);

	}
}
