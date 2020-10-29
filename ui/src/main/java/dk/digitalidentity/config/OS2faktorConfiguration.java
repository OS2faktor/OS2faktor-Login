package dk.digitalidentity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import dk.digitalidentity.config.modules.CoreData;
import dk.digitalidentity.config.modules.Cpr;
import dk.digitalidentity.config.modules.Scheduled;
import lombok.Getter;
import lombok.Setter;

@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "os2faktor.ui")
public class OS2faktorConfiguration {
	private Scheduled scheduled = new Scheduled();
	private Cpr cpr = new Cpr();
	private CoreData coreData = new CoreData();
}
