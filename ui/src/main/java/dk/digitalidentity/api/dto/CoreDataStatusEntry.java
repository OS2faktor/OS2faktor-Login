package dk.digitalidentity.api.dto;

import java.time.LocalDateTime;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.PersonStatistics;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoreDataStatusEntry {
	private String uuid;
	private String cpr;
	private String name;
	private String samAccountName;
	private boolean nsisAllowed;
	private NSISLevel nsisLevel;
	private boolean approvedConditions;
	private String approvedConditionsTts;
	private boolean lockedAdmin;
	private boolean lockedPerson;
	private boolean lockedDataset;
	private boolean lockedDead;
	private boolean lockedPassword;
	private boolean lockedExpired;
	private String lockedPasswordUntil;
	private LocalDateTime lastLogin;
	private boolean badPassword;
	private String badPasswordRule;
	private boolean mfaClients;

	public CoreDataStatusEntry(Person person, PersonStatistics statistics) {
		this.uuid = person.getUuid();
		this.cpr = person.getCpr();
		this.name = person.getName();
		this.samAccountName = person.getSamaccountName();
		this.nsisAllowed = person.isNsisAllowed();
		this.nsisLevel = person.getNsisLevel();
		this.approvedConditions = person.isApprovedConditions();
		this.approvedConditionsTts = (person.getApprovedConditionsTts() != null) ? person.getApprovedConditionsTts().toString() : null;
		this.lockedAdmin = person.isLockedAdmin();
		this.lockedPerson = person.isLockedPerson();
		this.lockedDataset = person.isLockedDataset();
		this.lockedDead = person.isLockedDead();
		this.lockedExpired = person.isLockedExpired();
		this.lockedPassword = person.isLockedPassword();
		this.lockedPasswordUntil = (person.getLockedPasswordUntil() != null) ? person.getLockedPasswordUntil().toString() : null;
		this.lastLogin = (statistics != null) ? statistics.getLastLogin() : null;
		this.badPassword = person.isBadPassword();
		this.badPasswordRule = (person.isBadPassword() && person.getBadPasswordRule() != null) ? person.getBadPasswordRule().getMessage() : null;
		this.mfaClients = (person.getMfaClients() != null && !person.getMfaClients().isEmpty());
	}
}
