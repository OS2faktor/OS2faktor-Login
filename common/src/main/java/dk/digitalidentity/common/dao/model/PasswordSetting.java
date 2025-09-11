package dk.digitalidentity.common.dao.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import dk.digitalidentity.common.dao.model.enums.PasswordHintsPosition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.validation.constraints.Size;
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

	@Column
	private Long maxLength;

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
	private boolean monitoringEnabled;

	@Column
	private boolean disallowOldPasswords;
	
	@Column
	private Long oldPasswordNumber;

	@Column
	private boolean disallowDanishCharacters;

	@Column
	private boolean disallowNameAndUsername;
	
	@Column
	@Size(max = 255)
	private String monitoringEmail;

	@Column
	private Long triesBeforeLockNumber;
	
	@Column
	private Long lockedMinutes;
	
	@Column
	private boolean maxPasswordChangesPrDayEnabled;
	
	@Column
	private Long maxPasswordChangesPrDay;

	@Column
	private boolean canNotChangePasswordEnabled;
	
	@Column
	private boolean preventBadPasswords;

	@Column
	private boolean specificSpecialCharactersEnabled;

	@Column
	private String allowedSpecialCharacters;
	
	@Column
	private boolean checkLeakedPasswords;

	@OneToOne
	@JoinColumn(name = "can_not_change_password_group_id", referencedColumnName = "id")
	private Group canNotChangePasswordGroup;

	@OneToOne
	@JoinColumn(name = "domain_id")
	private Domain domain;

	@Column
	@Enumerated(EnumType.STRING)
	private PasswordHintsPosition passwordHintsPosition;
}
