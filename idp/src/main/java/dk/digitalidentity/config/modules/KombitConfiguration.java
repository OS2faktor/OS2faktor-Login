package dk.digitalidentity.config.modules;

import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@Setter
@Getter
public class KombitConfiguration {
	private String metadataUrl = "https://adgangsstyring.stoettesystemerne.dk/runtime/saml2auth/metadata.idp";
	private String entityId = "https://saml.adgangsstyring.stoettesystemerne.dk";
	private String assuranceLevel = "3";
	private String cvr;
}
