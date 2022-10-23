package dk.digitalidentity.api.dto;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import dk.digitalidentity.common.dao.model.Person;
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
	private String subDomain;
	private boolean nsisAllowed;
	private Map<String, String> attributes;
	private String domain;
	private String expireTimestamp;
	private boolean transferToNemlogin;
	private String rid;

	public CoreDataEntry() {
		this.attributes = new HashMap<>();
	}

	public CoreDataEntry(Person person) {
		this.uuid = person.getUuid();
		this.cpr = person.getCpr();
		this.name = person.getName();
		this.email = person.getEmail();
		this.samAccountName = person.getSamaccountName();
		this.attributes = person.getAttributes();
		this.nsisAllowed = person.isNsisAllowed();
		this.transferToNemlogin = person.isTransferToNemlogin();
		this.rid = person.getRid();

		if (person.getExpireTimestamp() != null) {
			this.expireTimestamp = person.getExpireTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
		}

		if (person.getDomain().getParent() != null) {
			this.domain = person.getDomain().getParent().getName();
			this.subDomain = person.getDomain().getName();
		}
		else {
			this.domain = person.getDomain().getName();
		}
	}

	public static boolean compare(Person person, CoreDataEntry entry) {
		boolean cprEqual = Objects.equals(person.getCpr(), entry.getCpr());
		boolean uuidEqual = Objects.equals(person.getUuid().toLowerCase(), entry.getUuid().toLowerCase());
		boolean domainEqual = Objects.equals(person.getTopLevelDomain().getName().toLowerCase(), entry.getDomain().toLowerCase());
		boolean sAMAccountNameEqual = Objects.equals(person.getLowerSamAccountName(), ((entry.getSamAccountName() != null) ? entry.getSamAccountName().toLowerCase() : null));

		return cprEqual && uuidEqual && domainEqual && sAMAccountNameEqual;
	}

	public String getIdentifier() {
		return domain.toLowerCase() + ":" + uuid.toLowerCase() + ":" + cpr + ":" + ((samAccountName != null) ? samAccountName.toLowerCase() : "<null>");
	}
}
