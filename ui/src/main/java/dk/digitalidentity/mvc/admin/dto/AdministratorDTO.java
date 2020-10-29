package dk.digitalidentity.mvc.admin.dto;

import dk.digitalidentity.common.dao.model.Person;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdministratorDTO {
	private long id;
	private String userId;
	private String samaccountName;
	private String personName;
	private boolean admin;
	private boolean supporter;

	public AdministratorDTO(Person person) {
		this.id = person.getId();
		this.userId = person.getUserId();
		this.samaccountName = person.getSamaccountName();
		this.personName = person.getName();
		this.admin = person.isAdmin();
		this.supporter = person.isSupporter();
	}
}
