package dk.digitalidentity.common.dao.model;

import dk.digitalidentity.common.dao.model.enums.ForceMFARequired;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
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

	@Column
	private boolean delayedMobileLogin;
}
