package dk.digitalidentity.service.serviceprovider;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.http.client.HttpClient;
import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.saml.metadata.resolver.impl.AbstractReloadingMetadataResolver;
import org.opensaml.saml.saml2.core.AuthnContextClassRef;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.RequestedAuthnContext;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.springframework.util.StringUtils;

import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.SqlServiceProviderAdvancedClaim;
import dk.digitalidentity.common.dao.model.SqlServiceProviderCondition;
import dk.digitalidentity.common.dao.model.SqlServiceProviderConfiguration;
import dk.digitalidentity.common.dao.model.SqlServiceProviderGroupClaim;
import dk.digitalidentity.common.dao.model.SqlServiceProviderRequiredField;
import dk.digitalidentity.common.dao.model.SqlServiceProviderRoleCatalogueClaim;
import dk.digitalidentity.common.dao.model.SqlServiceProviderStaticClaim;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.dao.model.enums.Protocol;
import dk.digitalidentity.common.dao.model.enums.RequirementCheckResult;
import dk.digitalidentity.common.dao.model.enums.SqlServiceProviderConditionType;
import dk.digitalidentity.common.log.AuditLogger;
import dk.digitalidentity.common.service.AdvancedRuleService;
import dk.digitalidentity.common.service.DomainService;
import dk.digitalidentity.common.service.GroupService;
import dk.digitalidentity.common.service.rolecatalogue.RoleCatalogueService;
import dk.digitalidentity.controller.dto.LoginRequest;
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
    private AdvancedRuleService advancedRuleService;
    private AuditLogger auditLogger;

    public SqlServiceProvider(SqlServiceProviderConfiguration config, HttpClient httpClient, RoleCatalogueService roleCatalogueService, AdvancedRuleService advancedRuleService, AuditLogger auditLogger) {
        super.httpClient = httpClient;

        this.roleCatalogueService = roleCatalogueService;
        this.config = config;
        this.advancedRuleService = advancedRuleService;
        this.auditLogger = auditLogger;
    }

    @Override
    public EntityDescriptor getMetadata() throws RequesterException, ResponderException {

        if (resolver == null || !resolver.isInitialized()) {
        	// perform a sane cleanup in case broken data exists
        	try {
	        	if (resolver != null) {
	        		resolver.destroy();
	        	}
        	}
        	catch (Exception ignored) {
        		;
        	}
        	
            resolver = getMetadataResolver(getEntityId(), getMetadataUrl(), getMetadataContent());
        }
        
        // if never actually refreshed, perform a refresh
        if (resolver.getLastRefresh() == null) {
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
        String result = advancedRuleService.lookupField(person, config.getNameIdValue());
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
	public Map<String, Object> getAttributes(LoginRequest loginRequest, Person person, boolean includeDuplicates) {
		HashMap<String, Object> attributes = new HashMap<>();

		// "Static" fields
		Set<SqlServiceProviderStaticClaim> staticClaims = config.getStaticClaims();
		for (SqlServiceProviderStaticClaim staticClaim : staticClaims) {
			addAttribute(attributes, staticClaim.getField(), staticClaim.getValue(), includeDuplicates, false);
		}

		// Role Catalogue claims
		Set<SqlServiceProviderRoleCatalogueClaim> rcClaims = config.getRcClaims();
		for (SqlServiceProviderRoleCatalogueClaim rcClaim : rcClaims) {
			try {
				switch (rcClaim.getExternalOperation()) {
					case CONDITION_MEMBER_OF_SYSTEM_ROLE: {
						String systemRoleId = rcClaim.getExternalOperationArgument();
						if (roleCatalogueService.hasSystemRole(person, systemRoleId)) {
							addAttribute(attributes, rcClaim.getClaimName(), rcClaim.getClaimValue(), includeDuplicates, false);
						}
						break;
					}
					case CONDITION_MEMBER_OF_USER_ROLE: {
						String userRoleId = rcClaim.getExternalOperationArgument();
						if (roleCatalogueService.hasUserRole(person, userRoleId)) {
							addAttribute(attributes, rcClaim.getClaimName(), rcClaim.getClaimValue(), includeDuplicates, false);
						}
						break;
					}
					case GET_SYSTEM_ROLES: {
						String itSystemId = rcClaim.getExternalOperationArgument();
						List<String> systemRoles = roleCatalogueService.getSystemRoles(person, itSystemId);
						if (systemRoles != null && systemRoles.size() > 0) {
							addAttributeList(attributes, rcClaim.getClaimName(), systemRoles, includeDuplicates);
						}
						break;
					}
					case GET_SYSTEM_ROLES_OIOBPP: {
						String itSystemId = rcClaim.getExternalOperationArgument();
						String oiobpp = roleCatalogueService.getSystemRolesAsOIOBPP(person, itSystemId);
						if (oiobpp != null && oiobpp.length() > 0) {
							addAttribute(attributes, rcClaim.getClaimName(), oiobpp, includeDuplicates, false);
						}
						break;
					}
					case GET_USER_ROLES: {
						String itSystemId = rcClaim.getExternalOperationArgument();
						List<String> userRoles = roleCatalogueService.getUserRoles(person, itSystemId);
						if (userRoles != null && userRoles.size() > 0) {
							addAttributeList(attributes, rcClaim.getClaimName(), userRoles, includeDuplicates);
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
			String attribute = advancedRuleService.lookupField(person, requiredField.getPersonField());

			if (attribute != null) {
				addAttribute(attributes, requiredField.getAttributeName(), attribute, includeDuplicates, requiredField.isSingleValueOnly());
			}
		}

		// Advanced claims
		Set<SqlServiceProviderAdvancedClaim> advancedClaims = config.getAdvancedClaims();
		for (SqlServiceProviderAdvancedClaim advancedClaim : advancedClaims) {
			try {
				String attribute = advancedRuleService.evaluateRule(advancedClaim.getClaimValue(), person);

				if (StringUtils.hasLength(attribute)) {
					addAttribute(attributes, advancedClaim.getClaimName(), attribute, includeDuplicates, advancedClaim.isSingleValueOnly());
				}
			}
			catch (Exception ex) {
				auditLogger.failedClaimEvaluation(person, this.getName(null), advancedClaim.getClaimName(), ex.getMessage());
			}
		}

		// Group claims
		Set<SqlServiceProviderGroupClaim> groupClaims = config.getGroupClaims();
		for (SqlServiceProviderGroupClaim groupClaim : groupClaims) {
			if (GroupService.memberOfGroup(person, groupClaim.getGroup())) {
				addAttribute(attributes, groupClaim.getClaimName(), groupClaim.getClaimValue(), includeDuplicates, false);
			}
		}

		return attributes;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void addAttribute(HashMap<String, Object> attributes, String claimKey, String newClaimVal, boolean includeDuplicates, boolean singleValue) {
		if (!includeDuplicates) {
			if (singleValue) {
				attributes.put(claimKey, newClaimVal);
				return;
			} else {
				ArrayList<String> values = new ArrayList<>();
				values.add(newClaimVal);
				attributes.put(claimKey, values);
				return;
			}
		}

		// If value already exists, merge values
		Object computed = attributes.computeIfPresent(claimKey, (key, value) -> {
			if (value instanceof List) {
				((List) value).add(newClaimVal);
			} else {
				ArrayList<Object> newList = new ArrayList();
				newList.add(value);
				newList.add(newClaimVal);
				value = newList;
			}
			return value;
		});

		// if no key was found for attribute initialize list
		if (computed == null) {
			if (singleValue) {
				attributes.putIfAbsent(claimKey, newClaimVal);
			} else {
				ArrayList<String> values = new ArrayList<>();
				values.add(newClaimVal);

				attributes.putIfAbsent(claimKey, values);
			}
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void addAttributeList(HashMap<String, Object> attributes, String claimKey, List<String> newClaimVal, boolean includeDuplicates) {
		if (!includeDuplicates) {
			ArrayList<String> values = new ArrayList<>(newClaimVal);
			attributes.put(claimKey, values);
			return;
		}

		// If value (List) already exists, merge values
		Object computed = attributes.computeIfPresent(claimKey, (key, value) -> {
			if (value instanceof List) {
				((List) value).addAll(newClaimVal);
			}
			return value;
		});

		// if no key was found for attribute initialize list
		if (computed == null) {
			ArrayList<String> values = new ArrayList<>(newClaimVal);
			attributes.putIfAbsent(claimKey, values);
		}
	}

	public Set<SqlServiceProviderRequiredField> getRequiredFields() {
		return this.config.getRequiredFields();
	}

    public Set<SqlServiceProviderAdvancedClaim> getAdvancedClaims() {
        return this.config.getAdvancedClaims();
    }

    public Set<SqlServiceProviderRoleCatalogueClaim> getRcClaims() {
        return this.config.getRcClaims();
    }

    public Set<SqlServiceProviderGroupClaim> getGroupClaims() {
        return this.config.getGroupClaims();
    }

    @Override
    public boolean mfaRequired(LoginRequest loginRequest, Domain domain, boolean trustedIP) {
    	// check any NSIS requirements first
    	switch (nsisLevelRequired(loginRequest)) {
    		case HIGH:
        	case SUBSTANTIAL:
        		return true;
    		default:
    			break;
    	}

    	// then for SAML, check for any AuthnRequest supplied requirements
		if (Protocol.SAML20.equals(getProtocol()) && inspectAuthnRequestForMfaRequirement(loginRequest.getAuthnRequest())) {
			return true;
		}

		// finally check for any configured requirements
        switch (config.getForceMfaRequired()) {
            case ALWAYS:
            	// note, domain can be null in certain situations
            	if (domain != null) {
            		// if the domain has been exempted, do not require MFA for the login
            		if (this.config.getMfaExemptions().stream().map(e -> e.getDomain()).anyMatch(d -> Objects.equals(d.getId(), domain.getId()))) {
            			return false;
            		}
            	}
                return true;
            case ONLY_FOR_UNKNOWN_NETWORKS:
				if (!trustedIP) {
					return true;
				}
				break;
            default:
            	break;
        }

        return false;
    }

    private boolean inspectAuthnRequestForMfaRequirement(AuthnRequest authnRequest) {
        RequestedAuthnContext requestedAuthnContext = authnRequest.getRequestedAuthnContext();
        if (requestedAuthnContext != null && requestedAuthnContext.getAuthnContextClassRefs() != null) {
            for (AuthnContextClassRef authnContextClassRef : requestedAuthnContext.getAuthnContextClassRefs()) {
            	
            	// just add a series of magical checks here - various values can be supplied by various SP's, so we will extend the
            	// list over time to reflect that MFA is required ;)
            	
            	// this is what AULA uses sometimes :)
                if ("urn:dk:gov:saml:attribute:AssuranceLevel:3".equals(authnContextClassRef.getAuthnContextClassRef())) {
                    return true;
                }
            	// this is what AULA uses sometimes :)
                else if ("http://schemas.microsoft.com/claims/multipleauthn".equals(authnContextClassRef.getAuthnContextClassRef())) {
                    return true;
                }
            }
        }

        return false;
	}

    @Override
    public NSISLevel nsisLevelRequired(LoginRequest loginRequest) {
    	// get AuthnRequest supplied level
        NSISLevel requestedLevel = NSISLevel.NONE;

		if (Protocol.SAML20.equals(loginRequest.getProtocol())) {
			RequestedAuthnContext requestedAuthnContext = loginRequest.getAuthnRequest().getRequestedAuthnContext();
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
	public List<String> getEntityIds() {
		if (StringUtils.hasLength(config.getAdditionalEntityIds())) {
			List<String> entityIds = new ArrayList<String>();
			entityIds.add(config.getEntityId());
			
			String[] tokens = config.getAdditionalEntityIds().split(",");
			for (String token : tokens) {
				entityIds.add(token);
			}
			
			return entityIds;
		}

		return Collections.singletonList(config.getEntityId());
	}

    @Override
    public String getName(LoginRequest loginRequest) {
        return config.getName();
    }
    
	@Override
	public boolean requireOiosaml3Profile() {
		return config.isRequireOiosaml3Profile();
	}

	@Override
	public Long getPasswordExpiry() {
		return config.getCustomPasswordExpiry();
	}

	@Override
	public Long getMfaExpiry() {
		return config.getCustomMfaExpiry();
	}

	@Override
	public boolean isAllowAnonymousUsers() {
		return config.isAllowAnonymousUsers();
	}

	@Override
	public boolean allowUnsignedAuthnRequests() {
		return config.isAllowUnsignedAuthnRequests();
	}

	@Override
	public boolean disableSubjectConfirmation() { 
		return config.isDisableSubjectConfirmation();
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
    public Protocol getProtocol() {
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

	public void reloadMetadata(boolean recreateResolver) {

		if (recreateResolver || this.resolver == null) {
			// first attempt to cancel/destroy any pending tasks
			try {
				if (this.resolver != null) {
					this.resolver.destroy();
				}
			}
			catch (Exception ignored) {
				;
			}
			
			// this will cause the metadata to be refreshed on the next request
			this.resolver = null;

			// then attempt an immediate reload of metadata
			try {
				this.getMetadata();
			}
			catch (Exception ignored) {
				;
			}
		}
		else {
			try {
				this.resolver.refresh();
			}
			catch (Exception ex) {
				log.warn("Refresh of metadata failed for " + this.getEntityId(), ex);
			}
		}
	}

	@Override
	public boolean supportsNsisLoaClaim() {
		return false;
	}
	
	@Override
	public boolean preferNIST() {
		return config.isPreferNIST();
	}

	@Override
	public boolean isAllowMitidErvhervLogin() {
		return config.isAllowMitidErvhervLogin();
	}

	public String getCertificateAlias() {
		return config.getCertificateAlias();
	}

	@Override
	public boolean isDelayedMobileLogin(LoginRequest loginRequest) {
		return config.isDelayedMobileLogin();
	}
}
