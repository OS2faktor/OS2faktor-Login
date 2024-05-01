package dk.digitalidentity.common.dao.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

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
