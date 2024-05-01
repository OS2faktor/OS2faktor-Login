package dk.digitalidentity.common.config.modules.nemlogin;

import org.springframework.stereotype.Component;

import dk.digitalidentity.common.config.FeatureDocumentation;
import lombok.Getter;
import lombok.Setter;

@Component
@Getter
@Setter
public class NemloginBrokerConfiguration {

	@FeatureDocumentation(name = "NemLog-in som broker", description = "Anvend NemLog-in som en broker - kræver en broker-aftale med Digitaliseringsstyrelsen")
	private boolean brokerEnabled = false;
}
