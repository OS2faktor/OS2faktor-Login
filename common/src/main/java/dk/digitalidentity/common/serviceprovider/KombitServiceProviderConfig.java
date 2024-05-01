package dk.digitalidentity.common.serviceprovider;

import dk.digitalidentity.common.dao.model.enums.Protocol;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.enums.NameIdFormat;

@Component
public class KombitServiceProviderConfig implements ServiceProviderConfig {

	@Autowired
	private CommonConfiguration config;

    @Override
    public String getName() {
        return "KOMBIT Context Handler";
    }

    @Override
    public String getEntityId() {
        return config.getKombit().getEntityId();
    }

    @Override
    public Protocol getProtocol() {
        return Protocol.SAML20;
    }

    @Override
    public NameIdFormat getNameIdFormat() {
        return NameIdFormat.X509_SUBJECT_NAME;
    }

    @Override
    public String getMetadataUrl() {
        return config.getKombit().getMetadataUrl();
    }

    @Override
    public String getMetadataContent() {
        return null;
    }

    @Override
    public boolean isEnabled() {
        if (config.getKombit() == null || config.getKombit().isEnabled() == false) {
            return false;
        }

        return true;
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
        return config.getKombit().isEncryptAssertion();
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
