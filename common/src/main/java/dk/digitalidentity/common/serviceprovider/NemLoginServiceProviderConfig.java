package dk.digitalidentity.common.serviceprovider;

import dk.digitalidentity.common.dao.model.enums.Protocol;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.enums.NameIdFormat;

@Component
public class NemLoginServiceProviderConfig implements ServiceProviderConfig {

	@Autowired
	private CommonConfiguration config;

    @Override
    public String getName() {
        return "NemLog-in";
    }

    @Override
    public String getEntityId() {
        return config.getNemLoginTU().getEntityId();
    }

    @Override
    public Protocol getProtocol() {
        return Protocol.SAML20;
    }

    @Override
    public NameIdFormat getNameIdFormat() {
        return NameIdFormat.PERSISTENT;
    }

    @Override
    public String getMetadataUrl() {
        return config.getNemLoginTU().getMetadataUrl();
    }

    @Override
    public String getMetadataContent() {
        return null;
    }

    @Override
    public boolean isEnabled() {
        return config.getNemLoginTU().isEnabled();
    }

    @Override
    public boolean isPreferNemid() {
        return false;
    }

    @Override
    public boolean isNemLogInBrokerEnabled() {
        return false;
    }

    @Override
    public boolean isEncryptAssertions() {
        return config.getNemLoginTU().isEncryptAssertion();
    }
    
    @Override
    public boolean isPreferNIST() {
        return false;
    }

	@Override
	public boolean isRequireOiosaml3Profile() {
		return true;
	}
}
