package dk.digitalidentity.service.serviceprovider;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.saml.metadata.resolver.impl.HTTPMetadataResolver;
import org.opensaml.saml.saml2.core.AuthnContextClassRef;
import org.opensaml.saml.saml2.core.RequestedAuthnContext;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.dao.model.enums.Protocol;
import dk.digitalidentity.common.dao.model.enums.RequirementCheckResult;
import dk.digitalidentity.common.serviceprovider.SelfServiceServiceProviderConfig;
import dk.digitalidentity.controller.dto.LoginRequest;
import dk.digitalidentity.service.SessionHelper;
import dk.digitalidentity.util.Constants;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import lombok.SneakyThrows;
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
		return config.getNameIdFormat().value;
	}

	@Override
	public Map<String, Object> getAttributes(LoginRequest loginRequest, Person person, boolean includeDuplicates) {
		HashMap<String, Object> attributes = new HashMap<>();

		// we need to issue a claim with AAL, to support two scenarios inside the selfservice (cases where the user is NOT logged in with
		// an "erhvervsidentitet", but still need to perform certain operations. These are
		//
		// - locking and unlocking themselves in the UI (so managing life-cycle for the erhvervsidentitet using NemID/MitID)
		// - enrolling new MFA devices (if they use NemID/MitID to login, we will allow enrolling MFA devices at those levels, even if they did
		//   not have an "erhvervsidentitet" at that time.
		if (sessionHelper.isAuthenticatedWithNemIdOrMitId()) {
			NSISLevel authAssuranceLevel = sessionHelper.getMFALevel();
			
			if (authAssuranceLevel != null) {
				switch (authAssuranceLevel) {
					case SUBSTANTIAL:
					case HIGH: // HIGH is mapped to SUBSTANTIAL, as we do not support HIGH in our local scenario
						attributes.put(Constants.AUTHENTICATION_ASSURANCE_LEVEL, NSISLevel.SUBSTANTIAL.toString());
						break;
					default:
						break;
				}
			}
		}
		else {
			// in this case they actually have an active and valid "erhvervsidentitet", so we might as well issue the
			// same claim, to make it easier for the selfservice application to use this field (though using the LOA level and mapping it to AAL)
			NSISLevel levelOfAssurannce = sessionHelper.getLoginState();

			if (levelOfAssurannce != null) {
				switch (levelOfAssurannce) {
					case SUBSTANTIAL:
					case HIGH: // HIGH is mapped to SUBSTANTIAL, as we do not support HIGH in our local scenario
						attributes.put(Constants.AUTHENTICATION_ASSURANCE_LEVEL, NSISLevel.SUBSTANTIAL.toString());
						break;
					default:
						attributes.put(Constants.AUTHENTICATION_ASSURANCE_LEVEL, NSISLevel.NONE.toString());
						break;
					}
			}
		}

		return attributes;
	}

	@Override
	public boolean mfaRequired(LoginRequest loginRequest, Domain domain, boolean trustedIP) {
		return true;
	}

	@SneakyThrows
	@Override
	public NSISLevel nsisLevelRequired(LoginRequest loginRequest) {
    	// if the AuthnRequest supplies a required level, always use that
        RequestedAuthnContext requestedAuthnContext = loginRequest.getAuthnRequest().getRequestedAuthnContext();
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
		return config.isPreferNemid();
	}

	@Override
	public boolean nemLogInBrokerEnabled() {
		return false;
	}

	@Override
	public String getEntityId() {
		return config.getEntityId();
	}
	
	@Override
	public List<String> getEntityIds() {
		return Collections.singletonList(config.getEntityId());
	}

	@Override
	public String getName(LoginRequest loginRequest) {
		return config.getName();
	}

	@Override
	public RequirementCheckResult personMeetsRequirements(Person person) {
		return RequirementCheckResult.OK;
	}

	@Override
	public boolean encryptAssertions() {
		return config.isEncryptAssertions();
	}

	@Override
	public boolean enabled() {
		return config.isEnabled();
	}

	@Override
	public Protocol getProtocol() {
		return config.getProtocol();
	}

    public String getMetadataUrl() {
        return config.getMetadataUrl();
    }

	@Override
	public boolean supportsNsisLoaClaim() {
		return true;
	}
	
	@Override
	public boolean preferNIST() {
		return config.isPreferNIST();
	}
	
	@Override
	public boolean requireOiosaml3Profile() {
		return false;
	}

	@Override
	public Long getPasswordExpiry() {
		return null;
	}

	@Override
	public Long getMfaExpiry() {
		return null;
	}

	@Override
	public boolean onlyAllowLoginFromKnownNetworks() {
		return config.isOnlyAllowLoginFromKnownNetworks();
	}
}
