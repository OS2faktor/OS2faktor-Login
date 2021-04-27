package dk.digitalidentity.api.dto;

import dk.digitalidentity.common.dao.model.Person;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoreDataEntry {
	private String uuid;
	private String cpr;
	private String name;
	private String email;
	private String samAccountName;
	private String domain;
	private boolean nsisAllowed;
	private Map<String, String> attributes;

	public CoreDataEntry() {
		this.attributes = new HashMap<>();
	}

	public CoreDataEntry(Person person) {
		this.uuid = person.getUuid();
		this.cpr = person.getCpr();
		this.name = person.getName();
		this.email = person.getEmail();
		this.samAccountName = person.getSamaccountName();
		this.domain = person.getDomain().getName();
		this.attributes = person.getAttributes();
		this.nsisAllowed = person.isNsisAllowed();
	}

	// Compare method also exists on PersonDataDTO
	public static boolean compare(Person person, CoreDataEntry entry) {
		boolean cprEqual = Objects.equals(person.getCpr(), entry.getCpr());
		boolean uuidEqual = Objects.equals(person.getUuid(), entry.getUuid());
		boolean domainEqual = Objects.equals(person.getDomain().getName(), entry.getDomain());
		boolean sAMAccountNameEqual = Objects.equals(person.getSamaccountName(), entry.getSamAccountName());

		return cprEqual && uuidEqual && domainEqual && sAMAccountNameEqual;
	}
	
	public String getIdentifier() {
		return domain + ":" + uuid + ":" + cpr + ":" + ((samAccountName != null) ? samAccountName : "<null>");
	}
}
