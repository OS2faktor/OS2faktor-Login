package dk.digitalidentity.common.dao.model;


import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;

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
