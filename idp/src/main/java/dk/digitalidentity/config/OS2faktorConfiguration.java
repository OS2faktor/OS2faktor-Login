package dk.digitalidentity.config;

import dk.digitalidentity.config.modules.PasswordConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import dk.digitalidentity.config.modules.IdPKeystoreConfiguration;
import dk.digitalidentity.config.modules.KombitConfiguration;
import dk.digitalidentity.config.modules.RoleCatalogueConfiguration;
import dk.digitalidentity.config.modules.SelfServiceConfiguration;
import dk.digitalidentity.config.modules.StilConfiguration;
import lombok.Getter;
import lombok.Setter;

@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "os2faktor.idp")
public class OS2faktorConfiguration {
	private RoleCatalogueConfiguration roleCatalogue = new RoleCatalogueConfiguration();
	private IdPKeystoreConfiguration keystore = new IdPKeystoreConfiguration();
	private KombitConfiguration kombit = new KombitConfiguration();
	private StilConfiguration stil = new StilConfiguration();
	private SelfServiceConfiguration selfService = new SelfServiceConfiguration();
	private PasswordConfiguration password = new PasswordConfiguration();
	private String entityId;
	private String baseUrl;
}
