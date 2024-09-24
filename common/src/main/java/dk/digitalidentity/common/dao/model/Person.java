package dk.digitalidentity.common.dao.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.MapKeyColumn;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.hibernate.annotations.BatchSize;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;

import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.dao.model.mapping.PersonGroupMapping;
import lombok.Getter;
import lombok.Setter;

@Audited
@Entity
@Table(name = "persons")
@Setter
@Getter
@BatchSize(size = 100)
public class Person {

	@Id
	@Column
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;
	
	@NotNull
	@Column
	private String uuid;

	@NotNull
	@Column
	private String cpr;

	@Size(max = 255)
	@Column
	private String name;

	@Size(max = 255)
	@Column
	private String email;

	@Enumerated(EnumType.STRING)
	@Column
	private NSISLevel nsisLevel;

	@Column
	private boolean nsisAllowed;
	
	@Column
	private boolean admin;

	@Column
	private boolean serviceProviderAdmin;
	
	@Column
	private boolean userAdmin;

	@Column
	private boolean registrant;

	@Column
	private boolean kodeviserAdmin;

	@Column
	private boolean passwordResetAdmin;
	
	@Column
	private boolean approvedConditions;

	@Column
	private LocalDateTime approvedConditionsTts;
	
	@Column
	private boolean lockedAdmin;

	@Column
	private boolean lockedPerson;
	
	@Column
	private boolean lockedDataset;

	// does not lock the person object, only the password is locked when this is flagged (other login mechanisms still work, like MitID)
	@NotAudited
	@Column
	private boolean lockedPassword;

	@Column
	private boolean forceChangePassword;

	@Column
	private boolean lockedDead;

	@Column
	private boolean lockedDisenfranchised;

	@Column
	private boolean lockedExpired;

	@Column
	private LocalDateTime expireTimestamp;

	@NotAudited
	@Column
	private LocalDateTime lockedPasswordUntil;

	@NotAudited
	@Column
	private long badPasswordCount;

	@Size(max = 255)
	@Column
	private String nsisPassword;

	@Column
	private LocalDateTime nsisPasswordTimestamp;

	@Column
	private boolean doNotReplicatePassword;

	@Column
	private String samaccountName;

	// We never assign this field anymore, since we do not use NemId. But persons activated before the changeover to MitId will have a PID set in the database.
	// So we probably should keep this field to reflect the db.
	@Column
	private String nemIdPid;

	@Column
	private String mitIdNameId;
	
	@Column
	private boolean transferToNemlogin;
	
	@Column
	private boolean cprNameUpdated;
	
	@Column
	private String rid;
	
	@Column
	private String nemloginUserUuid;

	// Important to note that this field is an expiry date, NOT a "when-was-the-password-set"-date which is how nsisPasswordTimestamp works.
	@NotAudited
	@Column
	private LocalDateTime nextPasswordChange;

	@OneToOne
	@JoinColumn(name = "domain_id")
	private Domain domain;

	// NOTICE!
	// This field is actually a OneToOne, but we had to make it a OneToMany to help Hibernate do lazy-loading. To avoid having to
	// rewrite all the other code, the setter and getter methods for this field is custom-written below, to ensure a single entry
	// in this list (and allow empty lists)
	//
	// The longer story is that a nullable field used in a OneToOne cannot be lazy-loaded, as the Hibernate proxy would never be
	// null, preventing code from working with null values (including setting the value to null)
	@BatchSize(size = 100)
	@OneToMany(mappedBy = "person", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
	private List<Supporter> supporter;

	@Column
	private boolean nameProtected;

	@Size(max = 255)
	@Column
	private String nameAlias;
	
	@Column
	private String department;
	
	@NotAudited
	@Column
	private long dailyPasswordChangeCounter;
	
	@Column
	private String studentPassword;

	@Column
	private String ean;

	@Column
	private boolean institutionStudentPasswordAdmin;
	
	// TODO: kan en @BatchSize hjælpe her for at læse dem ud hurtigere?
	@NotAudited
	@ElementCollection
	@CollectionTable(name = "persons_attributes", joinColumns = { @JoinColumn(name = "person_id", referencedColumnName = "id") })
	@MapKeyColumn(name = "attribute_key")
	@Column(name = "attribute_value")
	private Map<String, String> attributes;

	// TODO: kan en @BatchSize hjælpe her for at læse dem ud hurtigere?
	@NotAudited
	@ElementCollection
	@CollectionTable(name = "persons_kombit_attributes", joinColumns = { @JoinColumn(name = "person_id", referencedColumnName = "id") })
	@MapKeyColumn(name = "attribute_key")
	@Column(name = "attribute_value")
	private Map<String, String> kombitAttributes;

	@NotAudited
	@BatchSize(size = 100)
	@OneToMany(fetch = FetchType.LAZY, mappedBy = "person")
	private List<PersonGroupMapping> groups;

	@NotAudited
	@BatchSize(size = 100)
	@OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY, mappedBy = "person", orphanRemoval = true)
	private List<KombitJfr> kombitJfrs;

	@NotAudited
	@BatchSize(size = 100)
	@OneToMany(fetch = FetchType.LAZY, mappedBy = "person", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<CachedMfaClient> mfaClients;

	@NotAudited
	@BatchSize(size = 100)
	@OneToMany(fetch = FetchType.LAZY, mappedBy = "person", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<SchoolRole> schoolRoles;

	public boolean isOnlyLockedByPerson() {
		return lockedPerson && !lockedAdmin && !lockedDataset && !isLockedCivilState() && !lockedExpired;
	}
	
	public boolean isLockedByOtherThanPerson() {
		return lockedAdmin || lockedDataset || isLockedCivilState() || lockedExpired;
	}

	public boolean isLocked() {
		return lockedPerson || lockedAdmin || lockedDataset || isLockedCivilState() || lockedExpired;
	}
	
	public boolean isLockedCivilState() {
		return lockedDead || lockedDisenfranchised;
	}

	public boolean hasActivatedNSISUser() {
		return isNsisAllowed() && NSISLevel.LOW.equalOrLesser(getNsisLevel());
	}

	public boolean isSupporter() {
		return supporter != null && supporter.size() > 0;
	}

	// please read the comment on the supporter field
	public void setSupporter(Supporter supporter) {
		if (supporter == null) {
			if (this.supporter != null) {
				this.supporter.clear();
			}
			else {
				this.supporter = new ArrayList<>();
			}

			return;
		}

		supporter.setPerson(this);
		if (this.supporter == null) {
			this.supporter = new ArrayList<>();
		}
		
		this.supporter.clear();
		this.supporter.add(supporter);
	}

	// please read the common on the supporter field
	public Supporter getSupporter() {
		if (this.supporter != null && this.supporter.size() > 0) {
			return this.supporter.get(0);
		}
		
		return null;
	}

	@JsonIgnore
	public Domain getTopLevelDomain() {
		if (domain.getParent() != null) {
			return domain.getParent();
		}
		
		return domain;
	}
	
	@JsonIgnore
	public String getLowerSamAccountName() {
		if (samaccountName != null) {
			return samaccountName.toLowerCase();
		}
		
		return null;
	}
	
	@JsonIgnore
	public String getAzureId() {
		if (attributes == null || attributes.size() == 0) {
			return null;
		}
		
		return attributes.get("azureId");
	}
	
	@JsonIgnore
	public String getAliasWithFallbackToName() {
		if (StringUtils.hasLength(nameAlias)) {
			return nameAlias;
		}
		
		return name;
	}
}
