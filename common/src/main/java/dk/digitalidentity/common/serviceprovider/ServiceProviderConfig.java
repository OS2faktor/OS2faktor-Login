package dk.digitalidentity.common.serviceprovider;

import dk.digitalidentity.common.dao.model.enums.KnownCertificateAliases;
import dk.digitalidentity.common.dao.model.enums.NameIdFormat;
import dk.digitalidentity.common.dao.model.enums.Protocol;

public interface ServiceProviderConfig {
    String getName();
    String getEntityId();
    Protocol getProtocol();
    NameIdFormat getNameIdFormat();
    String getMetadataUrl();
    String getMetadataContent();
    boolean isEnabled();
    boolean isPreferNemid();
    boolean isNemLogInBrokerEnabled();
    boolean isEncryptAssertions();
    boolean isPreferNIST();
    boolean isRequireOiosaml3Profile();
	
    default boolean isDelayedMobileLogin() {
        return true;
    }

	default boolean isDoNotMonitorCertificates() {
		return false;
	}

    default boolean isAllowMitidErvhervLogin() {
    	return false;
    }

    default boolean isAllowAnonymousUsers() {
    	return false;
    }

    default String getCertificateAlias() {
    	return KnownCertificateAliases.OCES.toString();
    }
}
