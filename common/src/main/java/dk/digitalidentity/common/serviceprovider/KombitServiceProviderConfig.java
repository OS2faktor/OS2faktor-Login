package dk.digitalidentity.common.serviceprovider;

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
    public String getProtocol() {
        return "SAML20";
    }

    @Override
    public String getNameIdFormat() {
        return NameIdFormat.X509_SUBJECT_NAME.value;
    }

    @Override
    public String getMetadataUrl() {
        return config.getKombit().getMetadataUrl();
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
    public boolean nemLogInBrokerEnabled() {
        return false;
    }

    @Override
    public boolean encryptAssertions() {
        return config.getKombit().isEncryptAssertion();
    }
    
    @Override
    public boolean preferNIST() {
        return false;
    }
}
