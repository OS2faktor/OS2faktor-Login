package dk.digitalidentity.common.dao.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.*;
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

	@Column(name = "complex_password")
	private boolean requireComplexPassword;

	@Column(name = "lowercase_letters")
	private boolean requireLowercaseLetters;
	
	@Column(name = "uppercase_letters")
	private boolean requireUppercaseLetters;

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
	private boolean disallowOldPasswords;

	@Column
	private boolean disallowDanishCharacters;

	@Column
	private boolean disallowNameAndUsername;
	
	@Column
	private String alternativePasswordChangeLink;

	@Column
	@Size(max = 255)
	private String monitoringEmail;

	@Column
	private boolean changePasswordOnUsersEnabled;

	@OneToOne
	@JoinColumn(name = "change_password_on_users_group_id")
	private Group changePasswordOnUsersGroup;

	@OneToOne
	@JoinColumn(name = "domain_id")
	private Domain domain;
}
