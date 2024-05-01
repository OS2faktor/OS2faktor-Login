package dk.digitalidentity.common.dao.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.Size;

import dk.digitalidentity.common.dao.model.enums.ForceMFARequired;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "kombit_subsystems")
@Setter
@Getter
public class KombitSubsystem {

	@Id
	@Column
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column
	@Size(max = 255)
	private String entityId;

	@Column(nullable = true)
	@Size(max = 255)
	private String name;
	
	@Column(nullable = true, name = "rc_identifier")
	@Size(max = 255)
	private String OS2rollekatalogIdentifier;

	// used to increase a given NSIS level supplied by the it-system
	@Column(nullable = true)
	@Enumerated(EnumType.STRING)
	private NSISLevel minNsisLevel;

	// used to enforce MFA on a specific it-system from KOMBIT
	@Column(name="force_mfa_required", nullable = false)
	@Enumerated(EnumType.STRING)
	private ForceMFARequired forceMfaRequired;
	
	@Column
	private boolean deleted;
}
