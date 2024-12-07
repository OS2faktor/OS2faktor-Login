package dk.digitalidentity.common.dao.model;

import java.util.Date;

import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.service.mfa.model.ClientType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "local_registered_mfa_clients")
public class LocalRegisteredMfaClient {

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
	
	@Column
	@NotNull
	private String cpr;
	
	@Enumerated(EnumType.STRING)
	@Column
	@NotNull
	private NSISLevel nsisLevel;

	@Column
	private Date associatedUserTimestamp;

	@Column
	private boolean prime;
}
