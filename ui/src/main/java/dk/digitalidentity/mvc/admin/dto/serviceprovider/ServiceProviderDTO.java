package dk.digitalidentity.mvc.admin.dto.serviceprovider;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import dk.digitalidentity.common.dao.model.SqlServiceProviderConfiguration;
import dk.digitalidentity.common.dao.model.SqlServiceProviderRequiredField;
import dk.digitalidentity.common.dao.model.SqlServiceProviderRoleCatalogueClaim;
import dk.digitalidentity.common.dao.model.SqlServiceProviderStaticClaim;
import dk.digitalidentity.common.dao.model.enums.SqlServiceProviderConditionType;
import dk.digitalidentity.common.dao.model.enums.ForceMFARequired;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.dao.model.enums.NameIdFormat;
import dk.digitalidentity.common.serviceprovider.KombitServiceProviderConfig;
import dk.digitalidentity.common.serviceprovider.ServiceProviderConfig;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ServiceProviderDTO {
	private ForceMFARequired forceMfaRequired;
	private NSISLevel nsisLevelRequired;
	private String id;
	private String name;
	private String entityId;
	private String protocol;
	private boolean enabled;
	private boolean preferNemid;
	private boolean encryptAssertions;
	private String metadataUrl;
	private String metadataContent;
	private List<CertificateDTO> certificates;
	private List<EndpointDTO> endpoints;
	private List<ClaimDTO> claims;
	private NameIdFormat nameIdFormat;
	private String nameIdValue;
	private boolean sqlServiceProvider;
	private boolean kombitServiceProvider;
	private List<ConditionDTO> conditionsDomains;
	private List<ConditionDTO> conditionsGroups;

	public ServiceProviderDTO() {
		this.id = "0";
		this.protocol = "SAML20";
		this.enabled = true;
		this.encryptAssertions = true;
	}

	public ServiceProviderDTO(SqlServiceProviderConfiguration config, List<CertificateDTO> certificates, List<EndpointDTO> endpoints) {
		this.id = Long.toString(config.getId());
		this.name = config.getName();
		this.protocol = config.getProtocol();
		this.entityId = config.getEntityId();
		this.enabled = config.isEnabled();
		this.preferNemid = config.isPreferNemid();
		this.encryptAssertions = config.isEncryptAssertions();
		this.metadataUrl = config.getMetadataUrl();
		this.metadataContent = config.getMetadataContent();
		this.nameIdFormat = config.getNameIdFormat();
		this.nameIdValue = config.getNameIdValue();
		this.forceMfaRequired = config.getForceMfaRequired();
		this.nsisLevelRequired = config.getNsisLevelRequired();
		this.certificates = certificates;
		this.endpoints = endpoints;
		this.sqlServiceProvider = true;
		this.kombitServiceProvider = false;

		// Add all claims together
		this.claims = new ArrayList<>();
		for (SqlServiceProviderRequiredField requiredField : config.getRequiredFields()) {
			claims.add(new ClaimDTO(requiredField));
		}

		for (SqlServiceProviderStaticClaim staticClaim : config.getStaticClaims()) {
			claims.add(new ClaimDTO(staticClaim));
		}
		
		for (SqlServiceProviderRoleCatalogueClaim rcClaim : config.getRcClaims()) {
			claims.add(new ClaimDTO(rcClaim));
		}

		this.conditionsDomains = config.getConditions()
				.stream()
				.filter(condition -> SqlServiceProviderConditionType.DOMAIN.equals(condition.getType()))
				.map(ConditionDTO::new)
				.collect(Collectors.toList());
		this.conditionsGroups = config.getConditions()
				.stream()
				.filter(condition -> SqlServiceProviderConditionType.GROUP.equals(condition.getType()))
				.map(ConditionDTO::new)
				.collect(Collectors.toList());
	}

	public ServiceProviderDTO(ServiceProviderConfig config, List<CertificateDTO> certificates, List<EndpointDTO> endpoints) throws Exception {
		this.id = config.getName();
		this.name = config.getName();
		this.protocol = config.getProtocol();
		this.entityId = config.getEntityId();
		this.enabled = config.enabled();
		this.preferNemid = config.preferNemId();
		this.encryptAssertions = config.encryptAssertions();
		this.metadataUrl = config.getMetadataUrl();
		this.metadataContent = config.getMetadataContent();
		this.sqlServiceProvider = false;
		this.kombitServiceProvider = (config instanceof KombitServiceProviderConfig);
		
		for (NameIdFormat format : NameIdFormat.values()) {
			if (Objects.equals(format.value, config.getNameIdFormat())) {
				nameIdFormat = format;
			}
		}

		this.certificates = certificates;
		this.endpoints = endpoints;
	}
}