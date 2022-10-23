package dk.digitalidentity.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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
	private StilConfiguration stil = new StilConfiguration();
	private SelfServiceConfiguration selfService = new SelfServiceConfiguration();
	private RoleCatalogueConfiguration roleCatalogue = new RoleCatalogueConfiguration();
	private RadiusConfiguration radiusConfiguration = new RadiusConfiguration();
	private Cpr cpr = new Cpr();
	private Nemlogin nemlogin = new Nemlogin();
	private MfaDatabase mfaDatabase = new MfaDatabase();
	private StilStudents stilStudent = new StilStudents();
}
