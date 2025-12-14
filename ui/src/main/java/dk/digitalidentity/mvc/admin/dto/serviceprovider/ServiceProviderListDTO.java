package dk.digitalidentity.mvc.admin.dto.serviceprovider;

import dk.digitalidentity.common.dao.model.SqlServiceProviderConfiguration;
import dk.digitalidentity.common.dao.model.enums.ForceMFARequired;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.serviceprovider.ServiceProviderConfig;
import lombok.Getter;

@Getter
public class ServiceProviderListDTO {
	private ForceMFARequired forceMfaRequired;
	private NSISLevel nsisLevelRequired;
	private String id;
	private String name;
	private String entityId;
	private String protocol;
	private boolean enabled;
	private boolean sqlServiceProvider;
	private boolean badMetadata;

	public ServiceProviderListDTO(final ServiceProviderConfig config) {
		this.forceMfaRequired = null;
		this.nsisLevelRequired = null;
		this.id = config.getName();
		this.name = config.getName();
		this.entityId = config.getEntityId();
		this.protocol = config.getProtocol().getPrettyName();
		this.enabled = config.isEnabled();
		this.sqlServiceProvider = false;
	}

	public ServiceProviderListDTO(final SqlServiceProviderConfiguration config) {
		this.forceMfaRequired = config.getForceMfaRequired();
		this.nsisLevelRequired = config.getNsisLevelRequired();
		this.id = Long.toString(config.getId());
		this.name = config.getName();
		this.entityId = config.getEntityId();
		this.protocol = config.getProtocol().getPrettyName();
		this.enabled = config.isEnabled();
		this.badMetadata = config.isBadMetadata();
		this.sqlServiceProvider = true;
	}
}
