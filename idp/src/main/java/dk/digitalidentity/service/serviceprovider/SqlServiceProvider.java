package dk.digitalidentity.service.serviceprovider;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.SqlServiceProviderConfiguration;
import dk.digitalidentity.common.dao.model.SqlServiceProviderRequiredField;
import dk.digitalidentity.common.dao.model.SqlServiceProviderStaticClaim;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.util.Constants;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import net.shibboleth.utilities.java.support.resolver.ResolverException;
import org.apache.http.client.HttpClient;
import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.saml.metadata.resolver.impl.HTTPMetadataResolver;
import org.opensaml.saml.saml2.core.AuthnContextClassRef;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.RequestedAuthnContext;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.springframework.stereotype.Component;

@Component
public class SqlServiceProvider extends ServiceProvider {

    private SqlServiceProviderConfiguration config;

    private HTTPMetadataResolver resolver;

    public SqlServiceProvider() {
        this.config = new SqlServiceProviderConfiguration();
    }

    public SqlServiceProvider(SqlServiceProviderConfiguration config, HttpClient httpClient) {
        super.httpClient = httpClient;
        this.config = config;
    }

    @Override
    public EntityDescriptor getMetadata() throws RequesterException, ResponderException {
        if (resolver == null || !resolver.isInitialized()) {
            resolver = getMetadataResolver(config.getEntityId(), config.getMetadataUrl());
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
        criteriaSet.add(new EntityIdCriterion(config.getEntityId()));

        try {
            return resolver.resolveSingle(criteriaSet);
        }
        catch (ResolverException ex) {
            throw new ResponderException("Konfigureret 'entityID' ikke fundet i metadata", ex);
        }
    }

    @Override
    public String getNameId(Person person) throws ResponderException {
        String requiredField = config.getNameIdValue();

        String result = null;
        switch (requiredField) {
            case "userId":
                result = person.getUserId();
                break;
            case "sAMAccountName":
                result = person.getSamaccountName();
                break;
            case "uuid":
                result = person.getUuid();
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
            throw new ResponderException("Brugeren har ikke det krævede 'NameId' felt (" + requiredField + ") i databasen");
        }

        return result;
    }

    @Override
    public String getNameIdFormat() {
        return config.getNameIdFormat();
    }

    @Override
    public Map<String, String> getAttributes(Person person) {
        HashMap<String, String> attributes = new HashMap<>();

        // "Static" fields
        Set<SqlServiceProviderStaticClaim> staticClaims = config.getStaticClaims();
        for (SqlServiceProviderStaticClaim staticClaim : staticClaims) {
            attributes.put(staticClaim.getField(), staticClaim.getValue());
        }

        // Person specific fields
        Set<SqlServiceProviderRequiredField> requiredFields = config.getRequiredFields();
        for (SqlServiceProviderRequiredField requiredField : requiredFields) {
            String attribute = null;
            switch (requiredField.getPersonField()) {
                case "userId":
                    attribute = person.getUserId();
                    break;
                case "sAMAccountName":
                    attribute = person.getSamaccountName();
                    break;
                case "uuid":
                    attribute = person.getUuid();
                    break;
                case "cpr":
                    attribute = person.getCpr();
                    break;
                default:
                    if (person.getAttributes() != null) {
                        attribute = person.getAttributes().get(requiredField.getPersonField());
                    }
            }

            if (attribute != null) {
                attributes.put(requiredField.getAttributeName(), attribute);
            }
        }

        return attributes;
    }

    @Override
    public boolean mfaRequired(AuthnRequest authnRequest) {
        switch (config.getForceMfaRequired()) {
            case ALWAYS:
                return true;
            case NEVER:
                return false;
            case DEPENDS:
                RequestedAuthnContext requestedAuthnContext = authnRequest.getRequestedAuthnContext();
                if (requestedAuthnContext != null) {
                    for (AuthnContextClassRef authnContextClassRef : requestedAuthnContext.getAuthnContextClassRefs()) {
                        if (Constants.LEVEL_OF_ASSURANCE_SUBSTANTIAL.equals(authnContextClassRef.getAuthnContextClassRef())) {
                            return true;
                        }

                        if (Constants.LEVEL_OF_ASSURANCE_LOW.equals(authnContextClassRef.getAuthnContextClassRef())) {
                            return false;
                        }
                    }
                }
        }
        return false;
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

        // If nsisLevelRequired is set in the config return this value
        NSISLevel nsisLevelRequired = config.getNsisLevelRequired();
        if (nsisLevelRequired != null) {
            return nsisLevelRequired;
        }

        return NSISLevel.NONE;
    }

    @Override
    public boolean preferNemId() {
        return config.isPreferNemid();
    }

    @Override
    public String getEntityId() throws RequesterException, ResponderException {
        return config.getEntityId();
    }

    @Override
    public String getName() {
        return config.getName();
    }

	@Override
	public boolean enabled() {
		// TODO: perhaps add this to the SQL schema
		return true;
	}
}
