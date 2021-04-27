package dk.digitalidentity.rest.admin.dto;

import java.util.Map;

import dk.digitalidentity.common.dao.model.Person;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PersonDataDTO {
	private long personId;
	private String uuid;
	private String cpr;
	private String name;	
	private String email;
	private boolean admin;
	private boolean supporter;
	private String samAccountName;
	private String domain;
	private Map<String, String> attributes;
	private boolean newPerson;
	private boolean nameProtected;

	public PersonDataDTO(Person person) {
		this.personId = person.getId();
		this.uuid = person.getUuid();
		this.cpr = person.getCpr();
		this.name = person.getName();
		this.email = person.getEmail();
		this.admin = person.isAdmin();
		this.supporter = person.isSupporter();
		this.samAccountName = person.getSamaccountName();
		this.domain = person.getDomain().getName();
		this.attributes = person.getAttributes();
		this.setNewPerson(false);
		this.nameProtected = person.isNameProtected();
	}

	// Compare method also exists on CoreDataEntry
	public static boolean compare(Person person, PersonDataDTO dto) {
		boolean cprEqual = Objects.equals(person.getCpr(), dto.getCpr());
		boolean uuidEqual = Objects.equals(person.getUuid(), dto.getUuid());
		boolean domainEqual = Objects.equals(person.getDomain().getName(), dto.getDomain());
		boolean sAMAccountNameEqual = Objects.equals(person.getSamaccountName(), dto.getSamAccountName());

		return cprEqual && uuidEqual && domainEqual && sAMAccountNameEqual;
	}
}
