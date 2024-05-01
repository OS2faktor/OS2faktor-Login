package dk.digitalidentity.config.modules;

import org.springframework.stereotype.Component;

import dk.digitalidentity.common.config.FeatureDocumentation;
import lombok.Getter;
import lombok.Setter;

@Component
@Setter
@Getter
public class AdminFeatures {
	
	@FeatureDocumentation(name = "Kodeordsadministrator", description = "Mulighed for at tildele rollen 'kodeordsadministrator' som gør det muligt at tildele kodeord til brugere der IKKE har en erhvervsidentitet")
	private boolean passwordResetEnabled = false;
}
