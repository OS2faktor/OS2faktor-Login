package dk.digitalidentity.service.serviceprovider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.http.client.HttpClient;
import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.saml.metadata.resolver.impl.AbstractReloadingMetadataResolver;
import org.opensaml.saml.saml2.core.AuthnContextClassRef;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.RequestedAuthnContext;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.SqlServiceProviderConfiguration;
import dk.digitalidentity.common.dao.model.SqlServiceProviderRequiredField;
import dk.digitalidentity.common.dao.model.SqlServiceProviderStaticClaim;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.service.RoleCatalogueService;
import dk.digitalidentity.util.Constants;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import lombok.extern.slf4j.Slf4j;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import net.shibboleth.utilities.java.support.resolver.ResolverException;

@Slf4j
@Component // TODO: not actually a component, so not autowire
public class SqlServiceProvider extends ServiceProvider {
    private SqlServiceProviderConfiguration config;
    private AbstractReloadingMetadataResolver resolver;
    private RoleCatalogueService roleCatalogueService;

    // TODO: default constructor never used (well, not once we remove the @Component annotation
    public SqlServiceProvider() {
        this.config = new SqlServiceProviderConfiguration();
    }

    public SqlServiceProvider(SqlServiceProviderConfiguration config, HttpClient httpClient, RoleCatalogueService roleCatalogueService) {
        super.httpClient = httpClient;

        this.roleCatalogueService = roleCatalogueService;
        this.config = config;
    }

    @Override
    public EntityDescriptor getMetadata() throws RequesterException, ResponderException {
        if (resolver == null || !resolver.isInitialized()) {
            resolver = getMetadataResolver(config.getEntityId(), config.getMetadataUrl(), config.getMetadataContent());
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
            case "name":
            	result = person.getName();
            	break;
            case "alias":
            	result = person.getNameAlias();
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
    public String getNameIdFormat() {
        return config.getNameIdFormat();
    }

    @Override
    public Map<String, Object> getAttributes(Person person) {
        HashMap<String, Object> attributes = new HashMap<>();

        // "Static" fields
        Set<SqlServiceProviderStaticClaim> staticClaims = config.getStaticClaims();
        for (SqlServiceProviderStaticClaim staticClaim : staticClaims) {
            attributes.put(staticClaim.getField(), staticClaim.getValue());
        }

        // Person specific fields
        Set<SqlServiceProviderRequiredField> requiredFields = config.getRequiredFields();
        for (SqlServiceProviderRequiredField requiredField : requiredFields) {
            Object attribute = null;
            
            if (requiredField.getPersonField().startsWith("os2rollekatalog")) {
            	String[] tokens = requiredField.getPersonField().split("\\|");
            	if (tokens.length != 3) {
            		log.error("Invalid configuration: " + requiredField.getPersonField());
            		continue;
            	}
            	else {
            		switch (tokens[1]) {
            			case "systemroles":
            				List<String> systemRoles = roleCatalogueService.getSystemRoles(person, tokens[2]);
            				if (systemRoles != null && systemRoles.size() > 0) {
            					attribute = systemRoles;
            				}
            				break;
        				default:
        					log.error("invalid lookup parameter: " + tokens[1]);
        					continue;
            		}
            	}
            }
            else {
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
	                case "name":
	                	attribute = person.getName();
	                	break;
	                case "alias":
	                	attribute = person.getNameAlias();
	                	break;
	                default:
	                    if (person.getAttributes() != null) {
	                        attribute = person.getAttributes().get(requiredField.getPersonField());
	                    }
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
    public boolean encryptAssertions() {
        return config.isEncryptAssertions();
    }

    @Override
	public boolean enabled() {
        return config.isEnabled();
	}
}
