package dk.digitalidentity.mvc.admin.dto;

import dk.digitalidentity.common.dao.model.PasswordSetting;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PasswordConfigurationForm {
	private long domainId;
	private long minLength;
	private boolean bothCapitalAndSmallLetters;
	private boolean requireDigits;
	private boolean requireSpecialCharacters;
	private boolean forceChangePasswordEnabled;
	private boolean disallowDanishCharacters;
	private boolean disallowOldPasswords;
	private Long forceChangePasswordInterval;
	private boolean replicateToAdEnabled;
	private boolean validateAgainstAdEnabled;
	private Long cacheAdPasswordInterval;
	private boolean monitoringEnabled;	
	private String monitoringEmail;

	public PasswordConfigurationForm(PasswordSetting settings) {
		this.minLength = settings.getMinLength();
		this.bothCapitalAndSmallLetters = settings.isBothCapitalAndSmallLetters();
		this.requireDigits = settings.isRequireDigits();
		this.requireSpecialCharacters = settings.isRequireSpecialCharacters();
		this.forceChangePasswordEnabled = settings.isForceChangePasswordEnabled();
		this.forceChangePasswordInterval = settings.getForceChangePasswordInterval();
		this.disallowOldPasswords = settings.isDisallowOldPasswords();
		this.replicateToAdEnabled = settings.isReplicateToAdEnabled();
		this.validateAgainstAdEnabled = settings.isValidateAgainstAdEnabled();
		this.cacheAdPasswordInterval = settings.getCacheAdPasswordInterval();
		this.monitoringEnabled = settings.isMonitoringEnabled();
		this.monitoringEmail = settings.getMonitoringEmail();
		this.disallowDanishCharacters = settings.isDisallowDanishCharacters();
		this.domainId = settings.getDomain().getId();
	}
}
