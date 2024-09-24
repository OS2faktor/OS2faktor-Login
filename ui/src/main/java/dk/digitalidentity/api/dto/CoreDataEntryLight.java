package dk.digitalidentity.api.dto;

import dk.digitalidentity.common.dao.model.Person;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoreDataEntryLight {
	private String cpr;
	private String name;
	private String email;
	private String samAccountName;

	public CoreDataEntryLight(Person person) {
		this.cpr = person.getCpr();
		this.name = person.getName();
		this.email = person.getEmail();
		this.samAccountName = person.getSamaccountName();
	}
}
