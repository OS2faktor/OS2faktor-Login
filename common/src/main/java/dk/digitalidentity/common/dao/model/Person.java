package dk.digitalidentity.common.dao.model;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.MapKeyColumn;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.hibernate.envers.Audited;

import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import lombok.Getter;
import lombok.Setter;

@Audited
@Entity
@Table(name = "persons")
@Setter
@Getter
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
	private boolean admin;
	
	@Column
	private boolean supporter;
	
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
	private boolean lockedPassword;

	@Column
	private LocalDateTime lockedPasswordUntil;

	@Column
	private long badPasswordCount;

	@Size(max = 255)
	@Column
	private String userId;

	@Size(max = 255)
	@Column
	private String nsisPassword;

	@Size(max = 255)
	@Column
	private String adPassword;

	@Column
	private String samaccountName;

	@Column
	private String domain;

	@Column
	private String nemIdPid;

	@ElementCollection
	@CollectionTable(name = "persons_attributes", joinColumns = { @JoinColumn(name = "person_id", referencedColumnName = "id") })
	@MapKeyColumn(name = "attribute_key")
	@Column(name = "attribute_value")
	private Map<String, String> attributes;

	public boolean isLocked() {
		return lockedAdmin || lockedPerson || lockedDataset || lockedPassword;
	}

	public static boolean compare(Person person1, Person person2) {
		boolean cprEqual = Objects.equals(person1.getCpr(), person2.getCpr());
		boolean uuidEqual = Objects.equals(person1.getUuid(), person2.getUuid());
		boolean domainEqual = Objects.equals(person1.getDomain(), person2.getDomain());
		boolean sAMAccountNameEqual = Objects.equals(person1.getSamaccountName(), person2.getSamaccountName());

		return cprEqual && uuidEqual && domainEqual && sAMAccountNameEqual;
	}

	public String getIdentifier() {
		return domain + ":" + uuid + ":" + cpr + ":" + ((samaccountName != null) ? samaccountName : "<null>");
	}

	public boolean hasNSISUser() {
		// You have to have at least NSIS level LOW and you need to have approved conditions
		return isApprovedConditions() && NSISLevel.LOW.equalOrLesser(getNsisLevel());
	}
}
