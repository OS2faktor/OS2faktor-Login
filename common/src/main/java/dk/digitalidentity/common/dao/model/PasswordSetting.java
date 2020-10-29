package dk.digitalidentity.common.dao.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

@Entity(name = "password_settings")
@Getter
@Setter
public class PasswordSetting {

	@Id
	@Column
	@JsonIgnore
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column
	private Long minLength;

	@Column(name = "capital_and_small_letters")
	private boolean bothCapitalAndSmallLetters;

	@Column(name = "digits")
	private boolean requireDigits;

	@Column(name = "special_characters")
	private boolean requireSpecialCharacters;
	
	@Column
	private boolean forceChangePasswordEnabled;
	
	@Column
	private Long forceChangePasswordInterval;
	
	@Column
	private boolean replicateToAdEnabled;
	
	@Column
	private boolean validateAgainstAdEnabled;
	
	@Column
	private boolean monitoringEnabled;
	
	@Column
	@Size(max = 255)
	private String monitoringEmail;
}
