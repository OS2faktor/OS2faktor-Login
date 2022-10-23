package dk.digitalidentity.mvc.admin.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LogWatchSettingsDto {
	private boolean enabled;
	private String alarmEmail;
	private boolean twoCountriesOneHourEnabled;
	private boolean tooManyWrongPasswordsEnabled;
	private long tooManyWrongPasswordsLimit;
	private boolean tooManyTimeLockedAccountsEnabled;
	private long tooManyTimeLockedAccountsLimit;
	private boolean tooManyAccountsLockedByAdminTodayEnabled;
	private long tooManyAccountsLockedByAdminTodayLimit;
	private boolean personDeadOrIncapacitatedEnabled;
}
