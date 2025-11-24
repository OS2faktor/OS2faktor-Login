package dk.digitalidentity.common.dao.model;

import java.time.LocalDate;
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
@Table(name = "keystores")
@Setter
@Getter
public class Keystore {

	@Id
	@Column
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column
	private String alias;

	@Column
	private String subjectDn;

	@Column
	private LocalDate expires;
	
	// is NULL if KMS is true
	@Column
	private byte[] keystore;
	
	// is NULL if KMS is true
	@Column
	private String password;
	
	@Column
	private LocalDateTime lastUpdated;

	// "soft-delete" feature - certificate is NOT loaded from the database if this is set
	@Column
	private boolean disabled;

}
