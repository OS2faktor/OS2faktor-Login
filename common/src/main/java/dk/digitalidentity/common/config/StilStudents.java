package dk.digitalidentity.common.config;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StilStudents {

	@FeatureDocumentation(name = "Kodeordsskifte på elever", description = "Gør det muligt for udvalgte skole-roller at skifte kodeord på elever")
	private boolean enabled;
	
	private String apiKey;
	private List<RoleSettingDTO> roleSettings = new ArrayList<>();
}
