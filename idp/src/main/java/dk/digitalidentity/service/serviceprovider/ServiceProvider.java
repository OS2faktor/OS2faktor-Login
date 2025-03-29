package dk.digitalidentity.service.serviceprovider;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.http.client.HttpClient;
import org.bouncycastle.util.encoders.Base64;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.metadata.resolver.impl.AbstractReloadingMetadataResolver;
import org.opensaml.saml.metadata.resolver.impl.HTTPMetadataResolver;
import org.opensaml.saml.metadata.resolver.impl.ResourceBackedMetadataResolver;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.KeyDescriptor;
import org.opensaml.saml.saml2.metadata.SPSSODescriptor;
import org.opensaml.saml.saml2.metadata.SingleLogoutService;
import org.opensaml.security.credential.UsageType;
import org.springframework.beans.factory.annotation.Autowired;

import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.dao.model.enums.Protocol;
import dk.digitalidentity.common.dao.model.enums.RequirementCheckResult;
import dk.digitalidentity.controller.dto.LoginRequest;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import dk.digitalidentity.util.StringResource;
import lombok.extern.slf4j.Slf4j;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.resolver.ResolverException;
import net.shibboleth.utilities.java.support.xml.BasicParserPool;

@Slf4j
public abstract class ServiceProvider {

    @Autowired
    protected HttpClient httpClient;

    public List<PublicKey> getSigningKeys() throws ResponderException, RequesterException {
        List<X509Certificate> certificates = getX509Certificate(UsageType.SIGNING);
        if (certificates.size() == 0) {
        	throw new RequesterException("Kunne ikke finde tjenesteudbyderens 'X509Certificate' af typen SIGNING");
        }

        return certificates.stream().map(c -> c.getPublicKey()).collect(Collectors.toList());
    }
    
    public X509Certificate getEncryptionCertificate() throws ResponderException, RequesterException {
    	List<X509Certificate> certificates = getX509Certificate(UsageType.ENCRYPTION);
        if (certificates.size() == 0) {
        	log.warn("Kunne ikke finde tjenesteudbyderens 'X509Certificate' af typen ENCRYPTION");
        	return null;
        }

		// Sort the certificates, so we don't accidentally pick certificates that are expired if any non-expired exists
		List<X509Certificate> sorted = certificates
				.stream()
				.sorted((o1, o2) -> Objects.compare(o2.getNotAfter(), o1.getNotAfter(), Date::compareTo))
				.collect(Collectors.toList());

		// Return certificate with longest shelf-life
		return sorted.get(0);
    }

    public SingleLogoutService getLogoutEndpoint() throws ResponderException, RequesterException {
        // Any post endpoint
        SPSSODescriptor ssoDescriptor = getMetadata().getSPSSODescriptor(SAMLConstants.SAML20P_NS);
        Optional<SingleLogoutService> match = ssoDescriptor
                .getSingleLogoutServices()
                .stream()
                .filter(singleLogoutService -> SAMLConstants.SAML2_POST_BINDING_URI.equals(singleLogoutService.getBinding()))
                .findFirst();

        // Any redirect endpoint
        if (match.isEmpty()) {
            match = ssoDescriptor
                    .getSingleLogoutServices()
                    .stream()
                    .filter(singleLogoutService -> SAMLConstants.SAML2_REDIRECT_BINDING_URI.equals(singleLogoutService.getBinding()))
                    .findFirst();
        }

        if (match.isEmpty()) {
            throw new ResponderException("Kunne ikke finde tjenesteudbyderens logout url i metadata");
        }

        return match.get();
    }

    public SingleLogoutService getLogoutResponseEndpoint() throws ResponderException, RequesterException {
        SPSSODescriptor ssoDescriptor = getMetadata().getSPSSODescriptor(SAMLConstants.SAML20P_NS);

        Optional<SingleLogoutService> match = ssoDescriptor
                .getSingleLogoutServices()
                .stream()
                .filter(sloService -> SAMLConstants.SAML2_POST_BINDING_URI.equals(sloService.getBinding()))
                .findFirst();

        if (match.isEmpty()) {
            match = ssoDescriptor
                    .getSingleLogoutServices()
                    .stream()
                    .filter(sloService -> SAMLConstants.SAML2_REDIRECT_BINDING_URI.equals(sloService.getBinding()))
                    .findFirst();
        }

        if (match.isEmpty()) {
            throw new RuntimeException("Could not find Post or Redirect SLO Response endpoint in metadata");
        }

        return match.get();
    }

    public List<X509Certificate> getX509Certificate(UsageType usageType) throws ResponderException, RequesterException {
    	List<X509Certificate> certificates = new ArrayList<>();
    	
        SPSSODescriptor ssoDescriptor = getMetadata().getSPSSODescriptor(SAMLConstants.SAML20P_NS);

        // Find X509Cert in Metadata filtered by type
        List<KeyDescriptor> keyDescriptors = ssoDescriptor.getKeyDescriptors().stream()
                .filter(keyDescriptor -> keyDescriptor.getUse().equals(usageType) || keyDescriptor.getUse() == null || keyDescriptor.getUse().equals(UsageType.UNSPECIFIED))
                .collect(Collectors.toList());

        for (KeyDescriptor keyDescriptor : keyDescriptors) {
        	try {
		        org.opensaml.xmlsec.signature.X509Certificate x509Certificate = keyDescriptor.getKeyInfo().getX509Datas().get(0).getX509Certificates().get(0);
		
		        byte[] bytes = Base64.decode(x509Certificate.getValue());
		        ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
		        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
		
	            certificates.add((X509Certificate) certificateFactory.generateCertificate(inputStream));
        	}
        	catch (Exception ex) {
        		log.error("Failed to parse service provider certificate - skipping entry", ex);
        	}
        }

        return certificates;
    }

    protected HTTPMetadataResolver getMetadataResolver(String entityId, String metadataURL) throws ResponderException, RequesterException {
        try {
            HTTPMetadataResolver resolver = new HTTPMetadataResolver(httpClient, metadataURL);
            resolver.setId(entityId);
            // allow expired metadata
            resolver.setRequireValidMetadata(false);
            
            resolver.setMinRefreshDelay(3 * 60 * 60 * 1000);
            resolver.setMaxRefreshDelay(3 * 60 * 60 * 1000);

            // Create paser pool for parsing metadata
            BasicParserPool parserPool = new BasicParserPool();
            resolver.setParserPool(parserPool);
            try {
                parserPool.initialize();
            }
            catch (ComponentInitializationException e) {
                throw new ResponderException("Kunne ikke initialisere HTTPMetadata læser", e);
            }

            // Initialize and save resolver for future use
            try {
                resolver.initialize();
            }
            catch (ComponentInitializationException e) {
                throw new RequesterException("Kunne ikke initialisere HTTPMetadata resolver", e);
            }

            return resolver;
        }
        catch (ResolverException e) {
            throw new ResponderException("Kunne ikke oprette MetadataResolver", e);
        }
    }

	protected AbstractReloadingMetadataResolver getMetadataResolver(String entityId, String metadataURL, String metadataContent) throws ResponderException, RequesterException {
		try {
			if (metadataURL != null && !metadataURL.isEmpty()) {
				return getMetadataResolver(entityId, metadataURL);
			}
			else if (metadataContent != null && !metadataContent.isEmpty()) {
				ResourceBackedMetadataResolver resolver = new ResourceBackedMetadataResolver(new StringResource(metadataContent, entityId));
				resolver.setId(entityId);

				// Create parser pool for parsing metadata
				BasicParserPool parserPool = new BasicParserPool();
				resolver.setParserPool(parserPool);
				try {
					parserPool.initialize();
				} 
				catch (ComponentInitializationException e) {
					throw new ResponderException("Kunne ikke initialisere HTTPMetadata læser", e);
				}

				// Initialize and save resolver for future use
				try {
					resolver.initialize();
				}
				catch (ComponentInitializationException e) {
					throw new RequesterException("Kunne ikke initialisere HTTPMetadata resolver", e);
				}

				return resolver;
			}
			else {
				throw new ResponderException("Enten metadataURL eller metadataContent skal konfigureres.");
			}
		}
		catch (IOException e) {
			throw new ResponderException("Kunne ikke oprette MetadataResolver", e);
		}
	}

	public boolean hasCustomSessionSettings() {
		return getPasswordExpiry() != null && getMfaExpiry() != null;
	}

	// SECTION: non-UI exposed setting, which is used to tweak certain SPs directly in the database
	
	public boolean allowUnsignedAuthnRequests() {
		// return true if the SP does not sign their AuthnRequests
		return false;
	}
	
	public boolean validateSignatureOnLogoutRequests() {
		// return false if an SP does not send signatures on LogoutRequests
		return true;
	}

	public boolean disableSubjectConfirmation() { 
		// return true if the SP does not support the SubjectConfirmation element
		return false;
	}

	public boolean isAllowAnonymousUsers() {
		return false;
	}

	public boolean isAllowMitidErvhervLogin() {
		return false;
	}
	
	public boolean alwaysIssueNistClaim() {
		// certain SPs always wants the NIST claim, even when issuing NSIS claims
		return false;
	}

	// END-SECTION

    public abstract EntityDescriptor getMetadata() throws RequesterException, ResponderException;
    public abstract String getNameId(Person person) throws ResponderException;
	public abstract String getNameIdFormat();
    public abstract Map<String, Object> getAttributes(LoginRequest loginRequest, Person person, boolean includeDuplicates);
    public abstract boolean mfaRequired(LoginRequest loginRequest, Domain domain, boolean trustedIP);
	public abstract boolean preferNemId();
	public abstract boolean nemLogInBrokerEnabled();
    public abstract String getEntityId();
	public abstract String getName(LoginRequest loginRequest);
    public abstract RequirementCheckResult personMeetsRequirements(Person person);
	public abstract boolean encryptAssertions();
	public abstract boolean enabled();
	public abstract Protocol getProtocol();
	public abstract boolean requireOiosaml3Profile();
	public abstract Long getPasswordExpiry();
	public abstract Long getMfaExpiry();

	/* These settings interact with each other - setting preferNIST = true overrules the last two, and
	 * no NSIS claim will ever be issued in that case.
	 * 
	 * Setting a required NSIS level will prevent logins if no NSIS level is available from the user,
	 * and will overrule the final setting - supportsNsisLoaClaim - this setting can be enabled while
	 * setting the required NSIS level to NONE, and it will still accept the NSIS Claim.
	 * 
	 * If requiredLevel is set to NONE, and supportsNsisClaim is false, no NSIS claim will be send,
	 * even if the user has an NSIS identity
	 */
	public abstract boolean preferNIST();
	public abstract boolean supportsNsisLoaClaim();
    public abstract NSISLevel nsisLevelRequired(LoginRequest loginRequest);

    /*
     * Additional EntityIDs used for identifying requests (google workspace uses this, perhaps others in the future)
     */
	public abstract List<String> getEntityIds();
}
