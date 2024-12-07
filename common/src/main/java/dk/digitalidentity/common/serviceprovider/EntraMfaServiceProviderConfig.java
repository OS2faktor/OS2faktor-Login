package dk.digitalidentity.common.serviceprovider;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.enums.NameIdFormat;
import dk.digitalidentity.common.dao.model.enums.Protocol;

@Component
public class EntraMfaServiceProviderConfig implements ServiceProviderConfig {

	@Autowired
	private CommonConfiguration commonConfiguration;
	
    @Override
    public String getName() {
        return "EntraID MFA";
    }

    @Override
    public String getEntityId() {
        return "https://login.microsoftonline.com/";
    }

    @Override
    public Protocol getProtocol() {
        return Protocol.ENTRAMFA;
    }

    @Override
    public NameIdFormat getNameIdFormat() {
        return NameIdFormat.EMAIL_ADDRESS;
    }

    @Override
    public String getMetadataUrl() {
    	return null;
    }

    @Override
    public String getMetadataContent() {
        return null;
    }

    @Override
    public boolean isEnabled() {
    	return commonConfiguration.getEntraMfa().isEnabled();
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
    	return false;
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
