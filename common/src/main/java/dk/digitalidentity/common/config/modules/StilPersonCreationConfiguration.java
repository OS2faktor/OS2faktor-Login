package dk.digitalidentity.common.config.modules;

import java.util.ArrayList;
import java.util.List;

import dk.digitalidentity.common.config.FeatureDocumentation;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StilPersonCreationConfiguration {
	
	@FeatureDocumentation(name = "Opret personer fra SkoleGrunddata", description = "Opretter personer fra SkoleGrunddata i OS2faktor")
	private boolean enabled = false;
	
	private boolean createEmployees;
	private boolean createStudents;
	
	private List<StilPersonCreationRoleSetting> roleSettings = new ArrayList<>();
}
