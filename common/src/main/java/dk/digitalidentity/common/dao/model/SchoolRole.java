package dk.digitalidentity.common.dao.model;

import java.util.List;

import org.hibernate.annotations.BatchSize;

import dk.digitalidentity.common.dao.model.enums.SchoolRoleValue;
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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "school_roles")
@Setter
@Getter
@BatchSize(size = 100)
public class SchoolRole {

	@Id
	@Column
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@NotNull
	@Column
	private String institutionId;
	
	@Column
	private String institutionName;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column
	private SchoolRoleValue role;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "person_id")
	private Person person;

	// note that only the mappings are cascade-saved/orphanRemoved - the SchoolClass mapped to is not cascade-saved nor orphanRemoved,
	// as that is an entity in its own right
	@BatchSize(size = 100)
	@OneToMany(mappedBy = "schoolRole", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<SchoolRoleSchoolClassMapping> schoolClasses;
}
