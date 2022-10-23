package dk.digitalidentity.common.dao.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import org.hibernate.envers.Audited;

import dk.digitalidentity.common.dao.model.enums.SchoolClassType;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Audited
@Entity
@Table(name = "school_classes")
@Setter
@Getter
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

	public String uniqueId() {
		return classIdentifier + ":" + institutionId;
	}
}
