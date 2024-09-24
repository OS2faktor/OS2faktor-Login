package dk.digitalidentity.common.dao.model;

import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.BatchSize;

import dk.digitalidentity.common.dao.model.enums.SchoolClassType;
import dk.digitalidentity.common.dao.model.mapping.SchoolClassPasswordWordMapping;
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
