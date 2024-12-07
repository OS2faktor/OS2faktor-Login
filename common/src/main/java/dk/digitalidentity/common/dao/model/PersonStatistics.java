package dk.digitalidentity.common.dao.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "person_statistics")
@Setter
@Getter
public class PersonStatistics {

	@Id
	@Column
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column
	private long personId;

	@Column
	private LocalDateTime lastLogin;

	@Column
	private LocalDateTime lastSelfServiceLogin;

	@Column
	private LocalDateTime lastPasswordChange;

	@Column
	private LocalDateTime lastUnlock;

	@Column(name = "last_mfa_use")
	private LocalDateTime lastMFAUse;

}
