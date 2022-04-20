package dk.digitalidentity.service.serviceprovider;

import java.util.HashMap;
import java.util.Map;

import dk.digitalidentity.common.dao.model.enums.RequirementCheckResult;
import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.saml.metadata.resolver.impl.AbstractReloadingMetadataResolver;
import org.opensaml.saml.saml2.core.AuthnContextClassRef;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.RequestedAuthnContext;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.serviceprovider.StilServiceProviderConfig;
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

			String metadataContent = null;
			try {
				metadataContent = getMetadataContent();
			} catch (Exception ex) {
				throw new ResponderException("Kunne ikke hente metadata fra fil", ex);
			}
			resolver = getMetadataResolver(getEntityId(), null, metadataContent);
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
	public Map<String, Object> getAttributes(AuthnRequest authnRequest, Person person) {
		Map<String, Object> map = new HashMap<>();

		// TODO: could argue that LOW or better would be fine until STIL requires actual NSIS
		if (NSISLevel.SUBSTANTIAL.equalOrLesser(sessionHelper.getMFALevel())) {
			map.put("dk:gov:saml:attribute:AssuranceLevel", "3");
		}
		else {
			map.put("dk:gov:saml:attribute:AssuranceLevel", "2");
		}

		if ("cpr".equals(commonConfig.getStil().getUniloginAttribute())) {
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
		return stilConfig.preferNemId();
	}

	@Override
	public String getEntityId() {
		return stilConfig.getEntityId();
	}

	@Override
	public String getName(AuthnRequest authnRequest) {
		return stilConfig.getName();
	}

	@Override
	public RequirementCheckResult personMeetsRequirements(Person person) {
		return RequirementCheckResult.OK;
	}

	@Override
	public boolean encryptAssertions() {
		return stilConfig.encryptAssertions();
	}

	@Override
	public String getNameIdFormat() {
		return stilConfig.getNameIdFormat();
	}

	public String getMetadataContent() throws Exception {
		return stilConfig.getMetadataContent();
	}

	@Override
	public boolean enabled() {
		return stilConfig.enabled();
	}

	@Override
	public String getProtocol() {
		return stilConfig.getProtocol();
	}
}
