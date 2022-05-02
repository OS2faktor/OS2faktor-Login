package dk.digitalidentity.common.config;

import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@Getter
@Setter
public class Nemlogin {

	@FeatureDocumentation(name = "NemLog-in som broker", description = "Anvend NemLog-in som en broker - kræver en broker-aftale med Digitaliseringsstyrelsen")
	private boolean brokerEnabled = false;
}
