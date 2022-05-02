package dk.digitalidentity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import dk.digitalidentity.config.modules.AuditLogModule;
import dk.digitalidentity.config.modules.CoreData;
import dk.digitalidentity.config.modules.EBoks;
import dk.digitalidentity.config.modules.GeoLocate;
import dk.digitalidentity.config.modules.IdP;
import dk.digitalidentity.config.modules.MfaPassthrough;
import dk.digitalidentity.config.modules.Scheduled;
import lombok.Getter;
import lombok.Setter;

@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "os2faktor.ui")
public class OS2faktorConfiguration {
	private Scheduled scheduled = new Scheduled();
	private CoreData coreData = new CoreData();
	private IdP idp = new IdP();
	private EBoks eboks = new EBoks();
	private GeoLocate geo = new GeoLocate();
	private AuditLogModule auditLog = new AuditLogModule();
	private MfaPassthrough mfaPassthrough = new MfaPassthrough();
}
