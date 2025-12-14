package dk.digitalidentity.common.dao.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.BatchSize;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;

import dk.digitalidentity.common.dao.listener.PersonListener;
import dk.digitalidentity.common.dao.model.enums.BadPasswordReason;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.dao.model.mapping.PersonGroupMapping;
import dk.digitalidentity.common.service.enums.ChangePasswordResult;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Audited
@Entity
@Table(name = "persons")
@Setter
@Getter
@BatchSize(size = 100)
@EntityListeners(PersonListener.class)
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

	@Column
	private LocalDateTime lockedDatasetTts;

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
	
	// flag that is set on users that have a more strict password policy
	@Column
	private boolean trustedEmployee;

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
	private String password;

	@Column
	private LocalDateTime passwordTimestamp;

	@Column
	private boolean doNotReplicatePassword;

	@NotAudited
	@Column
	private LocalDate badPasswordLeakCheckTts;
	
	@Column
	private boolean badPassword;
	
	@Enumerated(EnumType.STRING)
	@Column
	private BadPasswordReason badPasswordReason;
	
	@Enumerated(EnumType.STRING)
	@Column
	private ChangePasswordResult badPasswordRule;

	@Column
	private LocalDate badPasswordDeadlineTts;
	
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
	private boolean privateMitId;

	@Column
	private boolean qualifiedSignature;
	
	@Column
	private boolean cprNameUpdated;
	
	@Column
	private String nemloginUserUuid;
	
	// special field used to store the UUID from MitID Erhverv on external consultants, that are
	// allowed to use their corporate MitID to activate/reset the account. This requires that the
	// corporate account is registered at NSIS Substantial and that the CPR number is registered on
	// it, as we need to store that for other purposes (cpr name/civilstate validation, digital post integration, etc)
	@Column
	private String externalNemloginUserUuid;
	
	@Column
	private boolean robot;

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
	
	@NotAudited
	@BatchSize(size = 100)
	@ElementCollection
	@CollectionTable(name = "persons_attributes", joinColumns = { @JoinColumn(name = "person_id", referencedColumnName = "id") })
	@MapKeyColumn(name = "attribute_key")
	// OBS! max length is 768, as we need it in an index
	@Column(name = "attribute_value")
	private Map<String, String> attributes;

	@NotAudited
	@BatchSize(size = 100)
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
