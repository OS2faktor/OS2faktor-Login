package dk.digitalidentity.common.dao.model;


import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.Setter;

@Entity(name = "session_settings")
@Getter
@Setter
public class SessionSetting {

	@Id
	@Column
	@JsonIgnore
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column
	private Long passwordExpiry;
	
	@Column
	private Long mfaExpiry;

	@OneToOne
	@JoinColumn(name = "domain_id")
	private Domain domain;
}
