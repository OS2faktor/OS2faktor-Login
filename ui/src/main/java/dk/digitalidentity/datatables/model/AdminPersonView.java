package dk.digitalidentity.datatables.model;

import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
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
	private String name;

	@Column
	private String userId;

	@Column
	private String samaccountName;

	@Column
	private boolean locked;

	@Column
	private boolean lockedDataset;

	@Column
	private boolean lockedPerson;

	@Column
	private boolean lockedAdmin;

	@Column
	private boolean lockedPassword;
	
	@Column
	private String domain;
}
