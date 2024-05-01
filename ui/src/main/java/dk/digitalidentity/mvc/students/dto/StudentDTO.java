package dk.digitalidentity.mvc.students.dto;

import java.util.List;

import org.springframework.util.StringUtils;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.SchoolRole;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StudentDTO {
	private long id;
	private String name;
	private String samaccountName;
	private boolean canSeePassword;
	private List<SchoolRole> schoolRoles;

	public StudentDTO(Person person, boolean canSeePassword) {
		this.id = person.getId();
		this.name = person.getName();
		this.samaccountName = person.getSamaccountName();
		this.canSeePassword = StringUtils.hasLength(person.getStudentPassword()) && canSeePassword;
		this.schoolRoles = person.getSchoolRoles();		
	}
}
