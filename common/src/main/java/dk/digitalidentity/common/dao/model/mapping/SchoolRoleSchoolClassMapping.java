package dk.digitalidentity.common.dao.model.mapping;

import com.fasterxml.jackson.annotation.JsonBackReference;

import dk.digitalidentity.common.dao.model.SchoolClass;
import dk.digitalidentity.common.dao.model.SchoolRole;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;
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
