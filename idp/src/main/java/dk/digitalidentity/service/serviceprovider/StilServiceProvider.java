package dk.digitalidentity.service.serviceprovider;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.saml.metadata.resolver.impl.AbstractReloadingMetadataResolver;
import org.opensaml.saml.saml2.core.AuthnContextClassRef;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.RequestedAuthnContext;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.service.SessionHelper;
import dk.digitalidentity.util.Constants;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import net.shibboleth.utilities.java.support.resolver.ResolverException;

@Component
public class StilServiceProvider extends ServiceProvider {
	private AbstractReloadingMetadataResolver resolver;
	
	@Autowired
	private OS2faktorConfiguration configuration;
	
	@Autowired
	private SessionHelper sessionHelper;
	
	@Override
	public EntityDescriptor getMetadata() throws ResponderException, RequesterException {
		if (resolver == null || !resolver.isInitialized()) {
			String metadataContent = loadMetadata(configuration.getStil().getMetadataLocation());
			resolver = getMetadataResolver(configuration.getStil().getEntityId(), null, metadataContent);
		}

		// If last scheduled refresh failed, Refresh now to give up to date metadata
		if (!resolver.wasLastRefreshSuccess()) {
			try {
				resolver.refresh();
			}
			catch (ResolverException ex) {
				throw new RequesterException("Kunne ikke hente Metadata fra url", ex);
			}
		}

		// Extract EntityDescriptor by configured EntityID
		CriteriaSet criteriaSet = new CriteriaSet();
		criteriaSet.add(new EntityIdCriterion(configuration.getStil().getEntityId()));

		try {
			return resolver.resolveSingle(criteriaSet);
		}
		catch (ResolverException ex) {
			throw new ResponderException("Konfigureret 'entityID' ikke fundet i metadata", ex);
		}
	}

	@Override
	public String getNameId(Person person) throws ResponderException {
        String requiredField = configuration.getStil().getUniloginAttribute();

        String result = null;
        switch (requiredField) {
            case "sAMAccountName":
                result = person.getSamaccountName();
                break;
            case "cpr":
                result = person.getCpr();
                break;
            default:
                if (person.getAttributes() != null) {
                    result = person.getAttributes().get(requiredField);
                }
                break;
        }

        if (result == null) {
            throw new ResponderException("Brugeren har ikke det krævede 'Name ID' felt (" + requiredField + ") i databasen");
        }

        return result;
	}

	@Override
	public Map<String, Object> getAttributes(Person person) {
		Map<String, Object> map = new HashMap<>();

		// TODO: could argue that LOW or better would be fine until STIL requires actual NSIS
		if (NSISLevel.SUBSTANTIAL.equalOrLesser(sessionHelper.getMFALevel())) {
			map.put("dk:gov:saml:attribute:AssuranceLevel", "3");
		}
		else {
			map.put("dk:gov:saml:attribute:AssuranceLevel", "2");
		}

		if ("cpr".equals(configuration.getStil().getUniloginAttribute())) {
			map.put("dk:gov:saml:attribute:CprNumberIdentifier", person.getCpr());
		}

		return map;
	}

	@Override
	public boolean mfaRequired(AuthnRequest authnRequest) {
        RequestedAuthnContext requestedAuthnContext = authnRequest.getRequestedAuthnContext();
        if (requestedAuthnContext != null) {
            for (AuthnContextClassRef authnContextClassRef : requestedAuthnContext.getAuthnContextClassRefs()) {
                if (Constants.LEVEL_OF_ASSURANCE_SUBSTANTIAL.equals(authnContextClassRef.getAuthnContextClassRef())) {
                    return true;
                }
                
                if (Constants.STIL_LEVEL_OF_ASSURANCE_TOFAKTOR.equals(authnContextClassRef.getAuthnContextClassRef())) {
                    return true;
                }
            }
        }

        return false;
	}

	@Override
	public NSISLevel nsisLevelRequired(AuthnRequest authnRequest) {
		// TODO: eventually, but not yet
		return NSISLevel.NONE;
	}

	@Override
	public boolean preferNemId() {
		return false;
	}

	@Override
	public String getEntityId() throws RequesterException, ResponderException {
		return configuration.getStil().getEntityId();
	}

	@Override
	public String getName() {
		return "STIL UniLogin";
	}

	@Override
	public boolean encryptAssertions() {
		return configuration.getStil().isEncryptAssertion();
	}

	@Override
	public String getNameIdFormat() {
		return "urn:oasis:names:tc:SAML:2.0:nameid-format:persistent";
	}

	@Override
	public boolean enabled() {
		return configuration.getStil().isEnabled();
	}
	
	private String loadMetadata(String metadataLocation) throws ResponderException {
		try (InputStream is = new FileInputStream(metadataLocation)) {
		    StringBuilder builder = new StringBuilder();

		    try (Reader reader = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")))) {
    	        int c = 0;

    	        while ((c = reader.read()) != -1) {
    	            builder.append((char) c);
    	        }
    	    }
		    
		    return builder.toString();
		}
		catch (Exception ex) {
			throw new ResponderException(ex);
		}
	}
}
