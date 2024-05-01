package dk.digitalidentity.datatables.model;

import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.OneToMany;

import org.hibernate.annotations.BatchSize;

import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import lombok.Getter;
import lombok.Setter;

@Entity(name = "view_person_admin_identities")
@Getter
@Setter
public class AdminPersonView {

	@Id
	@Column
	private long id;

	@Enumerated(EnumType.STRING)
	@Column
	private NSISLevel nsisLevel;

	@Column
	private boolean nsisAllowed;

	@Column
	private String name;

	@Column
	private String userId;

	@Column
	private boolean locked;

	@Column
	private boolean lockedDataset;

	@Column
	private boolean lockedPerson;

	@Column
	private boolean lockedAdmin;

	@Column
	private boolean lockedExpired;
	
	@Column
	private boolean lockedCivilState;
	
	@Column
	private boolean lockedPassword;
	
	@Column
	private String domain;
	
	@Column 
	private String mfaClients;
	
	@Column
	private String approvedConditions;

	@BatchSize(size = 100)
	@OneToMany(fetch = FetchType.LAZY, mappedBy = "person")
	private List<PersonGroupView> groups;
}
