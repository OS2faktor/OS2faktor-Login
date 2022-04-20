package dk.digitalidentity.datatables.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

import lombok.Getter;
import lombok.Setter;

@Entity(name = "view_person_where_password_change_allowed")
@Getter
@Setter
public class PersonWherePasswordChangeAllowedView {

	@Id
	@Column
	private long id;

	@Column
	private String userId;

	@Column
	private String name;
	
	@Column
	private String domainName;

	@Column
	private long requiredGroupId;
}
