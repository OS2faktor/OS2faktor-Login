package dk.digitalidentity.service.serviceprovider;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.http.client.HttpClient;
import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.saml.metadata.resolver.impl.AbstractReloadingMetadataResolver;
import org.opensaml.saml.saml2.core.AuthnContextClassRef;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.RequestedAuthnContext;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;

import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.SqlServiceProviderCondition;
import dk.digitalidentity.common.dao.model.SqlServiceProviderConfiguration;
import dk.digitalidentity.common.dao.model.SqlServiceProviderRequiredField;
import dk.digitalidentity.common.dao.model.SqlServiceProviderRoleCatalogueClaim;
import dk.digitalidentity.common.dao.model.SqlServiceProviderStaticClaim;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.dao.model.enums.RequirementCheckResult;
import dk.digitalidentity.common.dao.model.enums.SqlServiceProviderConditionType;
import dk.digitalidentity.common.service.DomainService;
import dk.digitalidentity.common.service.GroupService;
import dk.digitalidentity.common.service.PersonService;
import dk.digitalidentity.common.service.RoleCatalogueService;
import dk.digitalidentity.util.Constants;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import lombok.extern.slf4j.Slf4j;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import net.shibboleth.utilities.java.support.resolver.ResolverException;

@Slf4j
public class SqlServiceProvider extends ServiceProvider {
    private SqlServiceProviderConfiguration config;
    private AbstractReloadingMetadataResolver resolver;
    private RoleCatalogueService roleCatalogueService;

    public SqlServiceProvider(SqlServiceProviderConfiguration config, HttpClient httpClient, RoleCatalogueService roleCatalogueService) {
        super.httpClient = httpClient;

        this.roleCatalogueService = roleCatalogueService;
        this.config = config;
    }

    @Override
    public EntityDescriptor getMetadata() throws RequesterException, ResponderException {
        if (resolver == null || !resolver.isInitialized()) {
            resolver = getMetadataResolver(getEntityId(), getMetadataUrl(), getMetadataContent());
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
        String result = lookupField(person, config.getNameIdValue());
        if (result == null) {
            throw new ResponderException("Brugeren har ikke det krævede 'Name ID' felt (" + config.getNameIdValue() + ") i databasen");
        }

        return result;
    }

    @Override
    public String getNameIdFormat() {
        return config.getNameIdFormat().value;
    }

    public String getMetadataUrl() {
        return config.getMetadataUrl();
    }

    public String getMetadataContent() {
        return config.getMetadataContent();
    }

    @Override
    public Map<String, Object> getAttributes(AuthnRequest authnRequest, Person person) {
        HashMap<String, Object> attributes = new HashMap<>();

        // "Static" fields
        Set<SqlServiceProviderStaticClaim> staticClaims = config.getStaticClaims();
        for (SqlServiceProviderStaticClaim staticClaim : staticClaims) {
            attributes.put(staticClaim.getField(), staticClaim.getValue());
        }

        // Role Catalogue claims
        Set<SqlServiceProviderRoleCatalogueClaim> rcClaims = config.getRcClaims();
        for (SqlServiceProviderRoleCatalogueClaim rcClaim : rcClaims) {
        	try {
        		switch (rcClaim.getExternalOperation()) {
	        		case CONDITION_MEMBER_OF_SYSTEM_ROLE: {
	        			String systemRoleId = rcClaim.getExternalOperationArgument();
	        			if (roleCatalogueService.hasSystemRole(person, systemRoleId)) {
	        				attributes.put(rcClaim.getClaimName(), rcClaim.getClaimValue());
	        			}
	        			break;
	        		}
	        		case CONDITION_MEMBER_OF_USER_ROLE: {
	        			String userRoleId = rcClaim.getExternalOperationArgument();
	        			if (roleCatalogueService.hasUserRole(person, userRoleId)) {
	        				attributes.put(rcClaim.getClaimName(), rcClaim.getClaimValue());
	        			}
	        			break;
	        		}
	        		case GET_SYSTEM_ROLES: {
	        			String itSystemId = rcClaim.getExternalOperationArgument();
	        			List<String> systemRoles = roleCatalogueService.getSystemRoles(person, itSystemId);
	        			if (systemRoles != null && systemRoles.size() > 0) {
        					attributes.put(rcClaim.getClaimName(), systemRoles);
	        			}
	        			break;
	        		}
	        		case GET_SYSTEM_ROLES_OIOBPP: {
	        			String itSystemId = rcClaim.getExternalOperationArgument();
	        			String oiobpp = roleCatalogueService.getSystemRolesAsOIOBPP(person, itSystemId);
	        			if (oiobpp != null && oiobpp.length() > 0) {
        					attributes.put(rcClaim.getClaimName(), oiobpp);
	        			}
	        			break;
	        		}
	        		case GET_USER_ROLES: {
	        			String itSystemId = rcClaim.getExternalOperationArgument();
	        			List<String> systemRoles = roleCatalogueService.getUserRoles(person, itSystemId);
	        			if (systemRoles != null && systemRoles.size() > 0) {
        					attributes.put(rcClaim.getClaimName(), systemRoles);
	        			}
	        			break;
	        		}
        		}
        	}
        	catch (Exception ex) {
        		// maybe change this to warning in the future, but error for now so we can monitor the functionality
        		log.error("Failed to execute role catalogue claim: " + rcClaim.getId() + " for person " + person.getId(), ex);
        	}
        }
        
        // Person specific fields
        Set<SqlServiceProviderRequiredField> requiredFields = config.getRequiredFields();
        for (SqlServiceProviderRequiredField requiredField : requiredFields) {
            String attribute = lookupField(person, requiredField.getPersonField());

            if (attribute != null) {
                attributes.put(requiredField.getAttributeName(), attribute);
            }
        }

        return attributes;
    }

	private String lookupField(Person person, String personField) {
		String attribute = null;
		
        switch (personField) {
            case "userId":
            case "sAMAccountName": // TODO: this is deprecated, but we are keeping it to support existing SPs setup with this value until they are migrated
                attribute = PersonService.getUsername(person);
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
            case "email":
            	attribute = person.getEmail();
            	break;
            case "firstname":
            	try {
            		int idx = person.getName().lastIndexOf(' ');
            		
            		if (idx > 0) {
            			attribute = person.getName().substring(0, idx);
            		}
            		else {
            			attribute = person.getName();
            		}
            	}
            	catch (Exception ex) {
            		log.error("Failed to parse name on " + person.getId(), ex);
            		attribute = person.getName();
            	}
            	break;
            case "lastname":
            	try {
            		int idx = person.getName().lastIndexOf(' ');
            		
            		if (idx > 0) {
            			attribute = person.getName().substring(idx + 1);
            		}
            		else {
            			attribute = person.getName();
            		}
            	}
            	catch (Exception ex) {
            		log.error("Failed to parse name on " + person.getId(), ex);
            		attribute = person.getName();
            	}
            	break;
            default:
                if (person.getAttributes() != null) {
                    attribute = person.getAttributes().get(personField);
                }
        }
        
        return attribute;
	}

	public Set<SqlServiceProviderRequiredField> getRequiredFields() {
		return this.config.getRequiredFields();
	}

    @Override
    public boolean mfaRequired(AuthnRequest authnRequest) {
        switch (config.getForceMfaRequired()) {
            case ALWAYS:
                return true;
            case NEVER:
                return false;
            case DEPENDS:
            	switch (nsisLevelRequired(authnRequest)) {
            		case HIGH:
	            	case SUBSTANTIAL:
	            		return true;
            		default:
            			return false;
            	}
        }

        return false;
    }

    @Override
    public NSISLevel nsisLevelRequired(AuthnRequest authnRequest) {
    	// get AuthnRequest supplied level
        NSISLevel requestedLevel = NSISLevel.NONE;
        RequestedAuthnContext requestedAuthnContext = authnRequest.getRequestedAuthnContext();
        if (requestedAuthnContext != null && requestedAuthnContext.getAuthnContextClassRefs() != null) {
            for (AuthnContextClassRef authnContextClassRef : requestedAuthnContext.getAuthnContextClassRefs()) {
                if (Constants.LEVEL_OF_ASSURANCE_SUBSTANTIAL.equals(authnContextClassRef.getAuthnContextClassRef())) {
                    requestedLevel = NSISLevel.SUBSTANTIAL;
                    break;
                }

                if (Constants.LEVEL_OF_ASSURANCE_LOW.equals(authnContextClassRef.getAuthnContextClassRef())) {
                    requestedLevel = NSISLevel.LOW;
                    break;
                }
            }
        }

        // get configured level
        NSISLevel configuredLevel = NSISLevel.NONE;
        if (config.getNsisLevelRequired() != null) {
            configuredLevel = config.getNsisLevelRequired();
        }

        // return the max of configuredLevel and requestedLevel
        ArrayList<NSISLevel> levels = new ArrayList<>();
        levels.add(requestedLevel);
        levels.add(configuredLevel);

        return Collections.max(levels, Comparator.comparingInt(NSISLevel::getLevel));
    }

    @Override
    public boolean preferNemId() {
        return config.isPreferNemid();
    }

	@Override
	public boolean nemLogInBrokerEnabled() {
		return config.isNemLogInBrokerEnabled();
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
        if (person == null) {
            return RequirementCheckResult.FAILED;
        }

        Set<SqlServiceProviderCondition> conditions = config.getConditions();
        if (conditions == null || conditions.isEmpty()) {
            return RequirementCheckResult.OK;
        }

        // Matches any of the domains (OR check)

        List<SqlServiceProviderCondition> domainConditions = conditions.stream().filter(condition -> SqlServiceProviderConditionType.DOMAIN.equals(condition.getType())).collect(Collectors.toList());
        boolean matchesDomainRules = false;
        if (domainConditions != null && !domainConditions.isEmpty()) {
        	if (DomainService.isMember(person, domainConditions.stream().map(c -> c.getDomain()).collect(Collectors.toList()))) {
                matchesDomainRules = true;
            }
        }
        else {
            // If no domain rules you automatically match domain rules
            matchesDomainRules = true;
        }

        if (!matchesDomainRules) {
            return RequirementCheckResult.FAILED_DOMAIN;
        }

        // Matches any of the groups (OR check)
        List<SqlServiceProviderCondition> groupConditions = conditions.stream().filter(condition -> SqlServiceProviderConditionType.GROUP.equals(condition.getType())).collect(Collectors.toList());
        boolean matchesGroupRules = false;
        if (groupConditions != null && !groupConditions.isEmpty()) {
        	if (GroupService.memberOfGroup(person, groupConditions.stream().map(c -> c.getGroup()).collect(Collectors.toList()))) {
                matchesGroupRules = true;
            }
        }
        else {
            // If no group rules you automatically match group rules
            matchesGroupRules = true;
        }

        if (!matchesGroupRules) {
            return RequirementCheckResult.FAILED_GROUP;
        }

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
    public String getProtocol() {
        return config.getProtocol();
    }

    public LocalDateTime getLastUpdated() {
        return config.getLastUpdated();
    }

    public void setConfig(SqlServiceProviderConfiguration config) {
        this.config = config;
    }

	public LocalDateTime getManualReloadTimestamp() {
		return this.config.getManualReloadTimestamp();
	}

	public void reloadMetadata() {
		// this will cause the metadata to be refreshed on the next request
		this.resolver = null;
	}

	@Override
	public boolean supportsNsisLoaClaim() {
		// TODO: extract into configuration
		return true;
	}
	
	@Override
	public boolean preferNIST() {
		return config.isPreferNIST();
	}
}
