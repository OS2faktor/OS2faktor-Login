package dk.digitalidentity.mvc.admin.dto;

import dk.digitalidentity.common.dao.model.PasswordSetting;
import dk.digitalidentity.common.dao.model.enums.PasswordHintsPosition;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PasswordConfigurationForm {
	private long domainId;
	private long minMinLength;
	private long minLength;
	private long maxLength;
	private boolean requireLowercaseLetters;
	private boolean requireUppercaseLetters;
	private boolean requireComplexPassword;
	private boolean requireDigits;
	private boolean requireSpecialCharacters;
	private boolean forceChangePasswordEnabled;
	private boolean disallowDanishCharacters;
	private boolean disallowOldPasswords;
	private Long oldPasswordNumber;
	private Long forceChangePasswordInterval;
	private boolean monitoringEnabled;	
	private String monitoringEmail;
	private boolean disallowNameAndUsername;
	private boolean showAdSettings;
	private Long triesBeforeLockNumber;
	private Long lockedMinutes;
	private boolean maxPasswordChangesPrDayEnabled;
	private Long maxPasswordChangesPrDay;
	private boolean canNotChangePasswordEnabled;
	private Long canNotChangePasswordGroup;
	private boolean preventBadPasswords;
	private boolean specificSpecialCharactersEnabled;
	private boolean checkLeakedPasswords;
	private String allowedSpecialCharacters;
	private PasswordHintsPosition passwordHintsPosition;

	public PasswordConfigurationForm(PasswordSetting settings) {
		this.minMinLength = 4;
		this.minLength = settings.getMinLength();
		this.maxLength = settings.getMaxLength();
		this.requireLowercaseLetters = settings.isRequireLowercaseLetters();
		this.requireUppercaseLetters = settings.isRequireUppercaseLetters();
		this.requireComplexPassword = settings.isRequireComplexPassword();
		this.requireDigits = settings.isRequireDigits();
		this.requireSpecialCharacters = settings.isRequireSpecialCharacters();
		this.forceChangePasswordEnabled = settings.isForceChangePasswordEnabled();
		this.forceChangePasswordInterval = settings.getForceChangePasswordInterval();
		this.disallowOldPasswords = settings.isDisallowOldPasswords();
		this.oldPasswordNumber = settings.getOldPasswordNumber();
		this.monitoringEnabled = settings.isMonitoringEnabled();
		this.monitoringEmail = settings.getMonitoringEmail();
		this.disallowDanishCharacters = settings.isDisallowDanishCharacters();
		this.disallowNameAndUsername = settings.isDisallowNameAndUsername();
		this.domainId = settings.getDomain().getId();
		this.showAdSettings = true;
		this.triesBeforeLockNumber = settings.getTriesBeforeLockNumber();
		this.lockedMinutes = settings.getLockedMinutes();
		this.maxPasswordChangesPrDayEnabled = settings.isMaxPasswordChangesPrDayEnabled();
		this.maxPasswordChangesPrDay = settings.getMaxPasswordChangesPrDay();
		this.canNotChangePasswordEnabled = settings.isCanNotChangePasswordEnabled();
		this.canNotChangePasswordGroup = settings.getCanNotChangePasswordGroup() != null ? settings.getCanNotChangePasswordGroup().getId() : null;
		this.preventBadPasswords = settings.isPreventBadPasswords();
		this.specificSpecialCharactersEnabled = settings.isSpecificSpecialCharactersEnabled();
		this.allowedSpecialCharacters = settings.getAllowedSpecialCharacters();
		this.checkLeakedPasswords = settings.isCheckLeakedPasswords();
		this.passwordHintsPosition = settings.getPasswordHintsPosition();
	}
}
