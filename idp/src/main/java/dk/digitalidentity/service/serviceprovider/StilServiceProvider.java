package dk.digitalidentity.service.serviceprovider;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.saml.metadata.resolver.impl.AbstractReloadingMetadataResolver;
import org.opensaml.saml.saml2.core.AuthnContextClassRef;
import org.opensaml.saml.saml2.core.RequestedAuthnContext;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.dao.model.enums.Protocol;
import dk.digitalidentity.common.dao.model.enums.RequirementCheckResult;
import dk.digitalidentity.common.serviceprovider.StilServiceProviderConfig;
import dk.digitalidentity.controller.dto.LoginRequest;
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
	private CommonConfiguration commonConfig;

	@Autowired
	private StilServiceProviderConfig stilConfig;
	
	@Autowired
	private SessionHelper sessionHelper;
	
	@Override
	public EntityDescriptor getMetadata() throws ResponderException, RequesterException {
		if (resolver == null || !resolver.isInitialized()) {
			resolver = getMetadataResolver(getEntityId(), stilConfig.getMetadataUrl(), null);
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
		criteriaSet.add(new EntityIdCriterion(getEntityId()));

		try {
			return resolver.resolveSingle(criteriaSet);
		}
		catch (ResolverException ex) {
			throw new ResponderException("Konfigureret 'entityID' ikke fundet i metadata", ex);
		}
	}

	@Override
	public String getNameId(Person person) throws ResponderException {
        String requiredField = commonConfig.getStil().getUniloginAttribute();

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
	public Map<String, Object> getAttributes(LoginRequest loginRequest, Person person, boolean includeDuplicates) {
		Map<String, Object> map = new HashMap<>();

		if ("cpr".equals(commonConfig.getStil().getUniloginAttribute())) {
			map.put("dk:gov:saml:attribute:CprNumberIdentifier", person.getCpr());
		}

		// supplement to LoA, as STIL needs these for mapping to OpenID Connect serviceproviders
		if (sessionHelper.hasUsedMFA()) {
			map.put("dk:unilogin:loa", "ToFaktor");
		}
		else {
			map.put("dk:unilogin:loa", "EnFaktor");
		}

		return map;
	}

	@Override
	public boolean mfaRequired(LoginRequest loginRequest, Domain domain, boolean trustedIP) {
		// allow list of configurable domains to always require MFA (for munis that want forced MFA on first login for employees)
		if (domain != null && commonConfig.getStil().getDomainIdsThatRequireMfa().contains(domain.getId())) {
			return true;
		}

        RequestedAuthnContext requestedAuthnContext = loginRequest.getAuthnRequest().getRequestedAuthnContext();
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
	public NSISLevel nsisLevelRequired(LoginRequest loginRequest) {
		// TODO: eventually, but not yet
		return NSISLevel.NONE;
	}

	@Override
	public boolean preferNemId() {
		return stilConfig.isPreferNemid();
	}

	@Override
	public boolean nemLogInBrokerEnabled() {
		return false;
	}

	@Override
	public String getEntityId() {
		return stilConfig.getEntityId();
	}
	
	@Override
	public List<String> getEntityIds() {
		return Collections.singletonList(stilConfig.getEntityId());
	}

	@Override
	public String getName(LoginRequest loginRequest) {
		return stilConfig.getName();
	}

	@Override
	public RequirementCheckResult personMeetsRequirements(Person person) {
		return RequirementCheckResult.OK;
	}

	@Override
	public boolean encryptAssertions() {
		return stilConfig.isEncryptAssertions();
	}

	@Override
	public String getNameIdFormat() {
		return stilConfig.getNameIdFormat().value;
	}

	@Override
	public boolean enabled() {
		return stilConfig.isEnabled();
	}

	@Override
	public Protocol getProtocol() {
		return stilConfig.getProtocol();
	}

	@Override
	public boolean supportsNsisLoaClaim() {
		return false;
	}
	
	@Override
	public boolean preferNIST() {
		return stilConfig.isPreferNIST();
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

	// STIL does not sign their logout requests correctly
	@Override
	public boolean validateSignatureOnLogoutRequests() {
		return false;
	}
}
