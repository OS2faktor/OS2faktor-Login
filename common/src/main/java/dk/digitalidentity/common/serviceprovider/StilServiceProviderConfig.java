package dk.digitalidentity.common.serviceprovider;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.enums.NameIdFormat;
import dk.digitalidentity.common.dao.model.enums.Protocol;

@Component
public class StilServiceProviderConfig implements ServiceProviderConfig {

    @Autowired
    private CommonConfiguration config;

    @Override
    public String getName() {
        return "STIL UniLogin";
    }

    @Override
    public String getEntityId() {
        return config.getStil().getEntityId();
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
    	return config.getStil().getMetadataUrl();
    }

    @Override
    public String getMetadataContent() {
    	return null;
    }

    @Override
    public boolean isEnabled() {
        return config.getStil().isEnabled();
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
    	return Objects.equals("cpr", config.getStil().getUniloginAttribute());
    }
    
    @Override
    public boolean isPreferNIST() {
    	return false;
    }

	@Override
	public boolean isRequireOiosaml3Profile() {
		return false;
	}
}
