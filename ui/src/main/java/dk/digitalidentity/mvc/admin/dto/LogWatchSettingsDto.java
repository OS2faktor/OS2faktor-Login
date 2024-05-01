package dk.digitalidentity.mvc.admin.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LogWatchSettingsDto {
	private boolean enabled;
	private String alarmEmail;
	private String whitelist;
	private long tooManyWrongPasswordsNonWhitelistLimit;
	private boolean tooManyWrongPasswordsNonWhitelistEnabled;
	private boolean twoCountriesOneHourEnabled;
	private boolean twoCountriesOneHourGermany;
	private boolean twoCountriesOneHourSweeden;
	private boolean tooManyWrongPasswordsEnabled;
	private long tooManyWrongPasswordsLimit;
	private boolean tooManyTimeLockedAccountsEnabled;
	private long tooManyTimeLockedAccountsLimit;
	private boolean personDeadOrIncapacitatedEnabled;
}
