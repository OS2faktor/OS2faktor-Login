package dk.digitalidentity.mvc.admin.dto.serviceprovider;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import dk.digitalidentity.common.dao.model.SqlServiceProviderAdvancedClaim;
import dk.digitalidentity.common.dao.model.SqlServiceProviderConfiguration;
import dk.digitalidentity.common.dao.model.SqlServiceProviderGroupClaim;
import dk.digitalidentity.common.dao.model.SqlServiceProviderRequiredField;
import dk.digitalidentity.common.dao.model.SqlServiceProviderRoleCatalogueClaim;
import dk.digitalidentity.common.dao.model.SqlServiceProviderStaticClaim;
import dk.digitalidentity.common.dao.model.enums.Protocol;
import dk.digitalidentity.common.dao.model.enums.SqlServiceProviderConditionType;
import dk.digitalidentity.common.dao.model.enums.ForceMFARequired;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.dao.model.enums.NameIdFormat;
import dk.digitalidentity.common.serviceprovider.KombitServiceProviderConfig;
import dk.digitalidentity.common.serviceprovider.KombitServiceProviderConfigV2;
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
	private boolean preferNIST;
	private boolean requireOiosaml3Profile;
	private boolean nemLogInBrokerEnabled;
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
	private List<String> redirectURLs;
	private List<Long> exemptedDomains;
	private String clientSecret;
	private boolean existingSecret;
	private boolean badMetadata;
	private Long passwordExpiry;
	private Long mfaExpiry;
	private boolean hasCustomSessionExpiry;
	private boolean allowMitidErhvervLogin;
	private boolean allowAnonymousUsers;

	public ServiceProviderDTO() {
		this.id = "0";
		this.protocol = Protocol.SAML20.name();
		this.enabled = true;
		this.encryptAssertions = true;
		this.conditionsDomains = new ArrayList<>();
		this.conditionsGroups = new ArrayList<>();
		this.exemptedDomains = new ArrayList<>();
		this.allowAnonymousUsers = false;
	}

	public ServiceProviderDTO(ServiceProviderConfig config, List<CertificateDTO> certificates, List<EndpointDTO> endpoints) {
		this.id = (config instanceof SqlServiceProviderConfiguration) ? Long.toString(((SqlServiceProviderConfiguration) config).getId()) : config.getName();
		this.name = config.getName();
		this.protocol = config.getProtocol().name();
		this.entityId = config.getEntityId();
		this.enabled = config.isEnabled();
		this.preferNemid = config.isPreferNemid();
		this.preferNIST = config.isPreferNIST();
		this.requireOiosaml3Profile = config.isRequireOiosaml3Profile();
		this.nemLogInBrokerEnabled = config.isNemLogInBrokerEnabled();
		this.encryptAssertions = config.isEncryptAssertions();
		this.metadataUrl = config.getMetadataUrl();
		this.metadataContent = config.getMetadataContent();
		this.sqlServiceProvider = (config instanceof SqlServiceProviderConfiguration);
		this.kombitServiceProvider = (config instanceof KombitServiceProviderConfig) || (config instanceof KombitServiceProviderConfigV2);
		this.hasCustomSessionExpiry = false;
		this.allowMitidErhvervLogin = config.isAllowMitidErvhervLogin();
		this.allowAnonymousUsers = config.isAllowAnonymousUsers();

		for (NameIdFormat format : NameIdFormat.values()) {
			if (Objects.equals(format.value, config.getNameIdFormat().value)) {
				nameIdFormat = format;
			}
		}

		if (config instanceof SqlServiceProviderConfiguration) {
			SqlServiceProviderConfiguration sqlConfig = (SqlServiceProviderConfiguration) config;

			this.nameIdFormat = sqlConfig.getNameIdFormat();
			this.nameIdValue = sqlConfig.getNameIdValue();
			this.forceMfaRequired = sqlConfig.getForceMfaRequired();
			this.nsisLevelRequired = sqlConfig.getNsisLevelRequired();
			this.badMetadata = sqlConfig.isBadMetadata();
			this.passwordExpiry = sqlConfig.getCustomPasswordExpiry();
			this.mfaExpiry = sqlConfig.getCustomMfaExpiry();

			if (passwordExpiry != null && mfaExpiry != null) {
				this.hasCustomSessionExpiry = true;
			}


			// Add all claims together
			this.claims = new ArrayList<>();
			for (SqlServiceProviderRequiredField requiredField : sqlConfig.getRequiredFields()) {
				claims.add(new ClaimDTO(requiredField));
			}

			for (SqlServiceProviderStaticClaim staticClaim : sqlConfig.getStaticClaims()) {
				claims.add(new ClaimDTO(staticClaim));
			}
			
			for (SqlServiceProviderRoleCatalogueClaim rcClaim : sqlConfig.getRcClaims()) {
				claims.add(new ClaimDTO(rcClaim));
			}
			
			for (SqlServiceProviderAdvancedClaim advClaim : sqlConfig.getAdvancedClaims()) {
				claims.add(new ClaimDTO(advClaim));
			}
			
			for (SqlServiceProviderGroupClaim groupClaim : sqlConfig.getGroupClaims()) {
				claims.add(new ClaimDTO(groupClaim));
			}

			this.conditionsDomains = sqlConfig.getConditions()
					.stream()
					.filter(condition -> SqlServiceProviderConditionType.DOMAIN.equals(condition.getType()))
					.map(ConditionDTO::new)
					.collect(Collectors.toList());

			this.conditionsGroups = sqlConfig.getConditions()
					.stream()
					.filter(condition -> SqlServiceProviderConditionType.GROUP.equals(condition.getType()))
					.map(ConditionDTO::new)
					.collect(Collectors.toList());
			
			this.exemptedDomains = sqlConfig.getMfaExemptions()
					.stream()
					.map(e -> e.getDomain().getId())
					.collect(Collectors.toList());
		}

		this.certificates = certificates;
		this.endpoints = endpoints;
	}
}