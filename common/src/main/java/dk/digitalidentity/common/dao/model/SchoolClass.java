package dk.digitalidentity.common.dao.model;

import java.util.List;

import org.hibernate.annotations.BatchSize;

import dk.digitalidentity.common.dao.model.enums.SchoolClassType;
import dk.digitalidentity.common.dao.model.mapping.SchoolClassPasswordWordMapping;
import dk.digitalidentity.common.dao.model.mapping.SchoolRoleSchoolClassMapping;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "school_classes")
@Setter
@Getter
@BatchSize(size = 100)
@ToString
public class SchoolClass {

	@Id
	@Column
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@NotNull
	@Column
	private String name;

	@NotNull
	@Column
	private String institutionId;

	@NotNull
	@Column
	private String classIdentifier;

	@Column
	private String level;
	
	@NotNull
	@Enumerated(EnumType.STRING)
	@Column
	private SchoolClassType type;
	
	@BatchSize(size = 100)
	@OneToMany(mappedBy = "schoolClass", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<SchoolClassPasswordWordMapping> passwordWords;

	@BatchSize(size = 100)
	@OneToMany(mappedBy = "schoolClass", fetch = FetchType.LAZY)
	private List<SchoolRoleSchoolClassMapping> roleMappings;
	
	public String uniqueId() {
		return classIdentifier + ":" + institutionId;
	}
	
	public boolean isIndskoling() {
		if (type == null || !type.equals(SchoolClassType.MAIN_GROUP)) {
			return false;
		}

		if (level == null) {
			return false;
		}

		String trimmedLevel = level.trim();
		if (trimmedLevel.equals("0") || trimmedLevel.equals("1") || trimmedLevel.equals("2") || trimmedLevel.equals("3")) {
			return true;
		}
		
		return false;
	}
}
