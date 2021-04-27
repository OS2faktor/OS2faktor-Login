package dk.digitalidentity.service.serviceprovider;

import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.service.SessionHelper;
import java.util.HashMap;
import java.util.Map;

import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.saml.metadata.resolver.impl.HTTPMetadataResolver;
import org.opensaml.saml.saml2.core.AuthnContextClassRef;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.RequestedAuthnContext;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.util.Constants;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import net.shibboleth.utilities.java.support.resolver.ResolverException;

@Component
public final class SelfServiceServiceProvider extends ServiceProvider {
	private HTTPMetadataResolver resolver;
	
	@Autowired
	private OS2faktorConfiguration configuration;

	@Autowired
	private SessionHelper sessionHelper;

	@Override
	public EntityDescriptor getMetadata() throws ResponderException, RequesterException {
		if (resolver == null || !resolver.isInitialized()) {
			resolver = getMetadataResolver(configuration.getSelfService().getEntityId(), configuration.getSelfService().getMetadataUrl());
		}

		// If last scheduled refresh failed, Refresh now to give up to date metadata
		if (!resolver.wasLastRefreshSuccess()) {
			try {
				resolver.refresh();
			}
			catch (ResolverException ex) {
				throw new RequesterException("Kunne ikke hente metadata fra url", ex);
			}
		}

		// Extract EntityDescriptor by configured EntityID
		CriteriaSet criteriaSet = new CriteriaSet();
		criteriaSet.add(new EntityIdCriterion(configuration.getSelfService().getEntityId()));

		try {
			return resolver.resolveSingle(criteriaSet);
		}
		catch (ResolverException ex) {
			throw new ResponderException("Konfigureret 'entityID' ikke fundet i metadata", ex);
		}
	}

	@Override
	public String getNameId(Person person) {
		return Long.toString(person.getId());
	}
	
	@Override
	public String getNameIdFormat() {
		return "urn:oasis:names:tc:SAML:2.0:nameid-format:persistent";
	}

	@Override
	public Map<String, Object> getAttributes(Person person) {
		String nemIDPid = sessionHelper.getNemIDPid();
		if (nemIDPid != null) {
			HashMap<String, Object> attributes = new HashMap<>();
			attributes.put("NemIDPid", nemIDPid);
			return attributes;
		}

		return null;
	}

	@Override
	public boolean mfaRequired(AuthnRequest authnRequest) {
		return true;
	}

	@Override
	public NSISLevel nsisLevelRequired(AuthnRequest authnRequest) {
    	// if the AuthnRequest supplies a required level, always use that
        RequestedAuthnContext requestedAuthnContext = authnRequest.getRequestedAuthnContext();
        if (requestedAuthnContext != null && requestedAuthnContext.getAuthnContextClassRefs() != null) {
            for (AuthnContextClassRef authnContextClassRef : requestedAuthnContext.getAuthnContextClassRefs()) {
                if (Constants.LEVEL_OF_ASSURANCE_SUBSTANTIAL.equals(authnContextClassRef.getAuthnContextClassRef())) {
                    return NSISLevel.SUBSTANTIAL;
                }

                if (Constants.LEVEL_OF_ASSURANCE_LOW.equals(authnContextClassRef.getAuthnContextClassRef())) {
                    return NSISLevel.LOW;
                }
            }
        }

		return NSISLevel.NONE;
	}

	@Override
	public boolean preferNemId() {
		return false;
	}

	@Override
	public String getEntityId() throws RequesterException, ResponderException {
		return configuration.getSelfService().getEntityId();
	}

	@Override
	public String getName() {
		return "OS2faktor selvbetjening";
	}

	@Override
	public boolean encryptAssertions() {
		return configuration.getSelfService().isEncryptAssertion();
	}

	@Override
	public boolean enabled() {
		if (configuration.getSelfService() == null || StringUtils.isEmpty(configuration.getSelfService().getMetadataUrl())) {
			return false;
		}

		return true;
	}
}
