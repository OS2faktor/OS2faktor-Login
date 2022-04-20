package dk.digitalidentity.config.modules;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Scheduled {
	private boolean enabled = false;
	private boolean cprNameSyncEnabled = true;
}
