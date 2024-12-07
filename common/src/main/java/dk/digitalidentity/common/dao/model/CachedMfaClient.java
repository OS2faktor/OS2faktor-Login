package dk.digitalidentity.common.dao.model;

import java.time.LocalDateTime;

import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.service.mfa.model.ClientType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "cached_mfa_client")
public class CachedMfaClient {

	@Id
	@Column
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column
	@NotNull
	private String name;

	@Enumerated(EnumType.STRING)
	@Column
	@NotNull
	private ClientType type;
	
	@Column
	@NotNull
	private String deviceId;
	
	@Enumerated(EnumType.STRING)
	@Column
	@NotNull
	private NSISLevel nsisLevel;

	@Column
	private String serialnumber;

	@Column
	private LocalDateTime lastUsed;

	@Column
	private LocalDateTime associatedUserTimestamp;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "person_id")
	private Person person;
}
