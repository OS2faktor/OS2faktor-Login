package dk.digitalidentity.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.config.modules.AdConfiguration;
import dk.digitalidentity.common.config.modules.CprConfiguration;
import dk.digitalidentity.common.config.modules.CustomerConfiguration;
import dk.digitalidentity.common.config.modules.DevConfiguration;
import dk.digitalidentity.common.config.modules.EntraMfaConfiguration;
import dk.digitalidentity.common.config.modules.FullServiceIdPConfiguration;
import dk.digitalidentity.common.config.modules.GeoLocate;
import dk.digitalidentity.common.config.modules.KombitConfiguration;
import dk.digitalidentity.common.config.modules.MailConfiguration;
import dk.digitalidentity.common.config.modules.MfaConfiguration;
import dk.digitalidentity.common.config.modules.MfaDatabaseConfiguration;
import dk.digitalidentity.common.config.modules.MitIDErhvervConfiguration;
import dk.digitalidentity.common.config.modules.PasswordSoonExpireConfiguration;
import dk.digitalidentity.common.config.modules.RadiusConfiguration;
import dk.digitalidentity.common.config.modules.RoleCatalogueConfiguration;
import dk.digitalidentity.common.config.modules.SelfServiceConfiguration;
import dk.digitalidentity.common.config.modules.StilPersonCreationConfiguration;
import dk.digitalidentity.common.config.modules.nemlogin.NemLoginIdMConfiguration;
import dk.digitalidentity.common.config.modules.nemlogin.NemLoginSPConfiguration;
import dk.digitalidentity.common.config.modules.school.StilSPConfiguration;
import dk.digitalidentity.common.config.modules.school.StudentPwdConfiguration;
import lombok.Getter;
import lombok.Setter;

@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "os2faktor.common")
public class CommonConfiguration {
	private MfaConfiguration mfa = new MfaConfiguration();
	private DevConfiguration dev = new DevConfiguration();
	private AdConfiguration ad = new AdConfiguration();
	private MailConfiguration email = new MailConfiguration();
	private CustomerConfiguration customer = new CustomerConfiguration();
	private KombitConfiguration kombit = new KombitConfiguration();
	private StilSPConfiguration stil = new StilSPConfiguration();
	private SelfServiceConfiguration selfService = new SelfServiceConfiguration();
	private RoleCatalogueConfiguration roleCatalogue = new RoleCatalogueConfiguration();
	private RadiusConfiguration radiusConfiguration = new RadiusConfiguration();
	private CprConfiguration cpr = new CprConfiguration();
	private MfaDatabaseConfiguration mfaDatabase = new MfaDatabaseConfiguration();
	private NemLoginIdMConfiguration nemLoginApi = new NemLoginIdMConfiguration();
	private NemLoginSPConfiguration nemLoginTU = new NemLoginSPConfiguration();
	private StudentPwdConfiguration stilStudent = new StudentPwdConfiguration();
	private PasswordSoonExpireConfiguration passwordSoonExpire = new PasswordSoonExpireConfiguration();
	private StilPersonCreationConfiguration stilPersonCreation = new StilPersonCreationConfiguration();
	private FullServiceIdPConfiguration fullServiceIdP = new FullServiceIdPConfiguration();
	private MitIDErhvervConfiguration mitIdErhverv = new MitIDErhvervConfiguration();
	private EntraMfaConfiguration entraMfa = new EntraMfaConfiguration();
	private GeoLocate geo = new GeoLocate();
}
