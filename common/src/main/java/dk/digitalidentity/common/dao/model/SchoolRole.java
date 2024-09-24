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
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.BatchSize;

import dk.digitalidentity.common.dao.model.enums.SchoolRoleValue;
import dk.digitalidentity.common.dao.model.mapping.SchoolRoleSchoolClassMapping;
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
