package dk.digitalidentity.common.config.modules;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordSoonExpireConfiguration {
	private int reminderDaysBeforeExpired = 7;
}
