package dk.digitalidentity.config;

import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import dk.digitalidentity.config.modules.AdminFeatures;
import dk.digitalidentity.config.modules.AppManager;
import dk.digitalidentity.config.modules.AuditLogModule;
import dk.digitalidentity.config.modules.CertManagerApi;
import dk.digitalidentity.config.modules.CoreData;
import dk.digitalidentity.config.modules.EBoks;
import dk.digitalidentity.config.modules.GeoLocate;
import dk.digitalidentity.config.modules.IdP;
import dk.digitalidentity.config.modules.MfaPassthrough;
import dk.digitalidentity.config.modules.PasswordChangeQueueApiConfiguration;
import dk.digitalidentity.config.modules.Scheduled;
import dk.digitalidentity.config.modules.UserAdministrationConfig;
import lombok.Getter;
import lombok.Setter;

@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "os2faktor.ui")
public class OS2faktorConfiguration {
	private String version = "2024 r5";
	private String latestVersion = "2024 r5";

	private Scheduled scheduled = new Scheduled();
	private CoreData coreData = new CoreData();
	private IdP idp = new IdP();
	private EBoks eboks = new EBoks();
	private GeoLocate geo = new GeoLocate();
	private AuditLogModule auditLog = new AuditLogModule();
	private MfaPassthrough mfaPassthrough = new MfaPassthrough();
	private AppManager appManager = new AppManager();
	private CertManagerApi certManagerApi = new CertManagerApi();
	private AdminFeatures adminFeatures = new AdminFeatures();
	private UserAdministrationConfig userAdministration = new UserAdministrationConfig();
	private PasswordChangeQueueApiConfiguration passwordChangeQueueApi = new PasswordChangeQueueApiConfiguration();

	private boolean landingPageEnabled = false;

	public boolean checkVersion() {
		return Objects.equals(version, latestVersion);
	}
}
