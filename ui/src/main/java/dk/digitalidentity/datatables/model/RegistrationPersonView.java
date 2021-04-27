package dk.digitalidentity.datatables.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;

import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import lombok.Getter;
import lombok.Setter;

@Entity(name = "view_person_pre_registered_identities")
@Getter
@Setter
public class RegistrationPersonView {

	@Id
	@Column
	private long id;

	@Enumerated(EnumType.STRING)
	@Column
	private NSISLevel nsisLevel;

	@Column
	private String cpr;

	@Column
	private String name;

	@Column
	private String samaccountName;
	
	@Column
	private String domain;

	@Column
	private boolean approvedConditions;
}
