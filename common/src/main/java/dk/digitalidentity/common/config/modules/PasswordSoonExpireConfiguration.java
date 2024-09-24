package dk.digitalidentity.common.config.modules;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordSoonExpireConfiguration {
	private int reminderDaysBeforeExpired = 7;
	
	// allow disabling the public change password page for those not wanting it available
	private boolean changePasswordPageEnabled = true;
}
