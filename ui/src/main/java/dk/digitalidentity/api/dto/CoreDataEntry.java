package dk.digitalidentity.api.dto;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import dk.digitalidentity.api.CoreDataApi;
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
	private boolean privateMitId;
	private boolean qualifiedSignature;
	private boolean lockedDataset;
	private boolean trustedEmployee;
	private boolean robot;
	private String department;
	private String externalNemloginUserUuid;
	private Set<String> roles;
	private String ean;

	public CoreDataEntry() {
		this.attributes = new HashMap<>();
		this.roles = new HashSet<>();
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
		this.privateMitId = person.isPrivateMitId();
		this.qualifiedSignature = person.isQualifiedSignature();
		this.lockedDataset = person.isLockedDataset();
		this.department = person.getDepartment();
		this.externalNemloginUserUuid = person.getExternalNemloginUserUuid();
		this.trustedEmployee = person.isTrustedEmployee();
		this.robot = person.isRobot();
		this.roles = new HashSet<>();
		this.ean = person.getEan();

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
		
		if (person.isAdmin()) {
			this.roles.add(CoreDataApi.PersonRoles.ADMIN.toString());
		}
		
		if (person.isKodeviserAdmin()) {
			this.roles.add(CoreDataApi.PersonRoles.KODEVISER_ADMIN.toString());
		}
		
		if (person.isSupporter()) {
			this.roles.add(CoreDataApi.PersonRoles.SUPPORTER.toString());
		}
		
		if (person.isUserAdmin()) {
			this.roles.add(CoreDataApi.PersonRoles.USER_ADMIN.toString());
		}
		
		if (person.isServiceProviderAdmin()) {
			this.roles.add(CoreDataApi.PersonRoles.TU_ADMIN.toString());
		}

		if (person.isRegistrant()) {
			this.roles.add(CoreDataApi.PersonRoles.REGISTRANT.toString());
		}
	}
	
	@JsonIgnore
	public String getLowerSamAccountName() {
		if (samAccountName != null) {
			return samAccountName.toLowerCase();
		}
		
		return null;
	}
	
	// make sure robot overrides any nsis/nemlogin stuff

	public boolean isNsisAllowed() {
		return nsisAllowed && !robot;
	}

	public boolean isTransferToNemlogin() {
		return transferToNemlogin && !robot;
	}
}
