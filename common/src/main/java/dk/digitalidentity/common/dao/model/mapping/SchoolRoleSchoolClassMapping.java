package dk.digitalidentity.common.dao.model.mapping;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonBackReference;

import dk.digitalidentity.common.dao.model.SchoolClass;
import dk.digitalidentity.common.dao.model.SchoolRole;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity(name = "school_roles_school_classes")
@Getter
@Setter
@NoArgsConstructor
public class SchoolRoleSchoolClassMapping {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@JsonBackReference
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "school_role_id")
	@NotNull
	private SchoolRole schoolRole;

	@JsonBackReference
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "school_class_id")
	@NotNull
	private SchoolClass schoolClass;

	public SchoolRoleSchoolClassMapping(SchoolRole schoolRole, SchoolClass SchoolClass) {
		this.schoolRole = schoolRole;
		this.schoolClass = SchoolClass;
	}
}
