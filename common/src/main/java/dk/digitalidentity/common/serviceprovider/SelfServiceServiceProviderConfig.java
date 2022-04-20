package dk.digitalidentity.common.serviceprovider;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.enums.NameIdFormat;

@Component
public class SelfServiceServiceProviderConfig implements ServiceProviderConfig {

    @Autowired
    private CommonConfiguration config;

    @Override
    public String getName() {
        return "OS2faktor selvbetjening";
    }

    @Override
    public String getEntityId() {
        return config.getSelfService().getEntityId();
    }

    @Override
    public String getProtocol() {
        return "SAML20";
    }

    @Override
    public String getNameIdFormat() {
        return NameIdFormat.PERSISTENT.value;
    }

    @Override
    public String getMetadataUrl() {
        return config.getSelfService().getMetadataUrl();
    }

    @Override
    public String getMetadataContent() throws Exception {
        return null;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public boolean preferNemId() {
        return false;
    }

    @Override
    public boolean encryptAssertions() {
        return config.getSelfService().isEncryptAssertion();
    }
}
