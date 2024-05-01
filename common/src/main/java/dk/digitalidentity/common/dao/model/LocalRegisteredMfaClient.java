package dk.digitalidentity.common.dao.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.service.mfa.model.ClientType;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

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
