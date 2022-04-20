package dk.digitalidentity.common.serviceprovider;

public interface ServiceProviderConfig {
    String getName();
    String getEntityId() throws Exception;
    String getProtocol();
    String getNameIdFormat();
    String getMetadataUrl();
    String getMetadataContent() throws Exception;
    boolean enabled();
    boolean preferNemId();
    boolean encryptAssertions();
}
