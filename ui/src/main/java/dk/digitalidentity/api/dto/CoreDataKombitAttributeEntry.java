package dk.digitalidentity.api.dto;

import java.util.HashMap;
import java.util.Map;

import dk.digitalidentity.common.dao.model.Person;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoreDataKombitAttributeEntry {
	private String samAccountName;
	private Map<String, String> kombitAttributes;

	public CoreDataKombitAttributeEntry() {
		this.kombitAttributes = new HashMap<>();
	}

	public CoreDataKombitAttributeEntry(Person person) {
		this.samAccountName = person.getSamaccountName();
		this.kombitAttributes = person.getKombitAttributes();
	}
}
