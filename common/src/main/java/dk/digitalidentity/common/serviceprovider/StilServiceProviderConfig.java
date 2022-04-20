package dk.digitalidentity.common.serviceprovider;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.enums.NameIdFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

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
    public String getProtocol() {
        return "SAML20";
    }

    @Override
    public String getNameIdFormat() {
        return NameIdFormat.PERSISTENT.value;
    }

    @Override
    public String getMetadataUrl() {
        return null;
    }

    @Override
    public String getMetadataContent() throws Exception {
        return loadMetadataFromFile(config.getStil().getMetadataLocation());
    }

    @Override
    public boolean enabled() {
        return config.getStil().isEnabled();
    }

    @Override
    public boolean preferNemId() {
        return false;
    }

    @Override
    public boolean encryptAssertions() {
        return config.getStil().isEncryptAssertion();
    }

    private String loadMetadataFromFile(String metadataLocation) throws Exception {


        try (InputStream is = new FileInputStream(metadataLocation)) {
            StringBuilder builder = new StringBuilder();

            try (Reader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                int c = 0;

                while ((c = reader.read()) != -1) {
                    builder.append((char) c);
                }
            }

            return builder.toString();
        }
    }
}
