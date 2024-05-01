package dk.digitalidentity.common.dao.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "keystores")
@Setter
@Getter
public class Keystore {

	@Id
	@Column
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column
	private String subjectDn;

	@Column
	private boolean primaryForIdp;

	@Column
	private boolean primaryForNemLogin;

	@Column
	private LocalDate expires;
	
	@Column
	private byte[] keystore;
	
	@Column
	private String password;
	
	@Column
	private LocalDateTime lastUpdated;

	// note, it is only possible to disable the secondary certificates, and only if the secondary bit is in alignment
	@Column
	private boolean disabled;
}
