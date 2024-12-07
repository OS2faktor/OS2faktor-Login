package dk.digitalidentity.common.config.modules.school;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import dk.digitalidentity.common.config.FeatureDocumentation;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Setter
@Getter
public class StilSPConfiguration {
	
	@FeatureDocumentation(name = "STIL integration", description = "Integration til STILs unilogin broker")
	private boolean enabled = false;
	
	private String entityId = "https://broker.unilogin.dk/auth/realms/broker";
	private String metadataBaseUrl = "https://stil-metadata.digital-identity.dk/metadata/";
	private String municipalityId = "TODO";
	private String uniloginAttribute = "cpr";
	private boolean encryptAssertion = false;
	private boolean nsisEnabled = false;
	private List<Long> domainIdsThatRequireMfa = new ArrayList<>();
	
	public String getMetadataUrl() {
		if ("TODO".equals(municipalityId)) {
			log.error("Attempted to get metadata url, but municipalityId was NOT configured!");
			return null;
		}
		
		return metadataBaseUrl + municipalityId.trim();
	}
}
