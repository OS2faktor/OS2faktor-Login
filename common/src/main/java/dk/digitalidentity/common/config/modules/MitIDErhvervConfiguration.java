package dk.digitalidentity.common.config.modules;

import dk.digitalidentity.common.config.FeatureDocumentation;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MitIDErhvervConfiguration {

	/*
	 * Before this feature can work, we need to enable persistent UUIDs in NemLog-in. This is done by creating a new set of metadata, that contains
	 * the following extra required attribute
	 * 
	 *       <md:RequestedAttribute FriendlyName="Persistent UUID" Name="https://data.gov.dk/model/core/eid/professional/uuid/persistent" NameFormat="urn:oasis:names:tc:SAML:2.0:attrname-format:uri" isRequired="true" />
	 * 
	 * when added, uploaded and approved, we get a persistent UUID during login, which is the same UUID as the end-users can see in MitID Erhverv
	 */
	@FeatureDocumentation(name = "MitID Erhverv", description = "Gør det muligt for eksterne at aktivere sig med deres MitID Erhverv, og dermed kunne gennemføre aktiveringen uden brug af MitID Privat")
	private boolean enabled = false;

	// only enable this for purely corporate customers running the solution as a non-NSIS Identity Provider
	private boolean allowMissingCpr = false;
}
