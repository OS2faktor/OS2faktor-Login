package dk.digitalidentity.common.serviceprovider;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.enums.NameIdFormat;

@Component
public class KombitTestServiceProviderConfigV2 implements ServiceProviderConfig {

	@Autowired
	private CommonConfiguration config;

    @Override
    public String getName() {
        return "KOMBIT Context Handler 2 (NSIS TEST)";
    }

    @Override
    public String getEntityId() {
        return config.getKombit().getEntityIdTestv2();
    }

    @Override
    public String getMetadataUrl() {
        return config.getKombit().getMetadataUrlTestv2();
    }
    
    // same as prod from here on
    
    @Override
    public String getProtocol() {
        return "SAML20";
    }

    @Override
    public String getNameIdFormat() {
        return NameIdFormat.X509_SUBJECT_NAME.value;
    }

    @Override
    public String getMetadataContent() throws Exception {
        return null;
    }

    @Override
    public boolean enabled() {
        if (config.getKombit() == null || config.getKombit().isEnabled() == false) {
            return false;
        }

        return true;
    }

    @Override
    public boolean preferNemId() {
        return false;
    }

    @Override
    public boolean encryptAssertions() {
        return config.getKombit().isEncryptAssertion();
    }

	@Override
	public boolean nemLogInBrokerEnabled() {
		return false;
	}
	
	@Override
    public boolean preferNIST() {
        return false;
    }
}
