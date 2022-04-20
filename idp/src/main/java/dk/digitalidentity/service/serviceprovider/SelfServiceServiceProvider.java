package dk.digitalidentity.service.serviceprovider;

import java.util.HashMap;
import java.util.Map;

import dk.digitalidentity.common.dao.model.enums.RequirementCheckResult;
import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.saml.metadata.resolver.impl.HTTPMetadataResolver;
import org.opensaml.saml.saml2.core.AuthnContextClassRef;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.RequestedAuthnContext;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.serviceprovider.SelfServiceServiceProviderConfig;
import dk.digitalidentity.service.SessionHelper;
import dk.digitalidentity.util.Constants;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import net.shibboleth.utilities.java.support.resolver.ResolverException;

@Component
public final class SelfServiceServiceProvider extends ServiceProvider {
	private HTTPMetadataResolver resolver;

	@Autowired
	private SelfServiceServiceProviderConfig config;

	@Autowired
	private SessionHelper sessionHelper;

	@Override
	public EntityDescriptor getMetadata() throws ResponderException, RequesterException {
		if (resolver == null || !resolver.isInitialized()) {
			resolver = getMetadataResolver(config.getEntityId(), getMetadataUrl());
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
		criteriaSet.add(new EntityIdCriterion(config.getEntityId()));

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
		return config.getNameIdFormat();
	}

	@Override
	public Map<String, Object> getAttributes(AuthnRequest authnRequest, Person person) {
		String nemIDPid = sessionHelper.getNemIDPid();
		HashMap<String, Object> attributes = new HashMap<>();

		if (nemIDPid != null) {
			attributes.put("NemIDPid", nemIDPid);
		}

		if (sessionHelper.isAuthenticatedWithNemIdOrMitId()) {
			NSISLevel authAssuranceLevel = sessionHelper.getMFALevel();

			if (authAssuranceLevel != null) {
				switch (authAssuranceLevel) {
					case SUBSTANTIAL:
					case HIGH: // HIGH is mapped to SUBSTANTIAL, as we do not support HIGH in our local issue
						attributes.put(Constants.AUTHENTICATION_ASSURANCE_LEVEL, NSISLevel.SUBSTANTIAL.toString());
						break;
					default:
						break;
				}
			}
		}
		else {
			NSISLevel authAssuranceLevel = sessionHelper.getLoginState();

			if (authAssuranceLevel != null) {
				switch (authAssuranceLevel) {
					case SUBSTANTIAL:
					case HIGH: // HIGH is mapped to SUBSTANTIAL, as we do not support HIGH in our local issue
						attributes.put(Constants.AUTHENTICATION_ASSURANCE_LEVEL, NSISLevel.SUBSTANTIAL.toString());
						break;
					default:
						break;
					}
			}
		}

		return attributes;
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
		return config.preferNemId();
	}

	@Override
	public String getEntityId() {
		return config.getEntityId();
	}

	@Override
	public String getName(AuthnRequest authnRequest) {
		return config.getName();
	}

	@Override
	public RequirementCheckResult personMeetsRequirements(Person person) {
		return RequirementCheckResult.OK;
	}

	@Override
	public boolean encryptAssertions() {
		return config.encryptAssertions();
	}

	@Override
	public boolean enabled() {
		return config.enabled();
	}

	@Override
	public String getProtocol() {
		return config.getProtocol();
	}

    public String getMetadataUrl() {
        return config.getMetadataUrl();
    }

}
