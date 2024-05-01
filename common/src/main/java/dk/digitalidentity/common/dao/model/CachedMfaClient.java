package dk.digitalidentity.common.dao.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.service.mfa.model.ClientType;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

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
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "person_id")
	private Person person;
}
