package dk.digitalidentity.service.serviceprovider;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
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

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.KombitJfr;
import dk.digitalidentity.common.dao.model.KombitSubsystem;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.ForceMFARequired;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.dao.model.enums.Protocol;
import dk.digitalidentity.common.dao.model.enums.RequirementCheckResult;
import dk.digitalidentity.common.dao.model.enums.SettingKey;
import dk.digitalidentity.common.service.AdvancedRuleService;
import dk.digitalidentity.common.service.KombitSubSystemService;
import dk.digitalidentity.common.service.RoleCatalogueService;
import dk.digitalidentity.common.service.SettingService;
import dk.digitalidentity.common.serviceprovider.KombitServiceProviderConfigV2;
import dk.digitalidentity.controller.dto.LoginRequest;
import dk.digitalidentity.util.Constants;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import lombok.extern.slf4j.Slf4j;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import net.shibboleth.utilities.java.support.resolver.ResolverException;

@Slf4j
@Component
public class KombitServiceProviderV2 extends ServiceProvider {
	private HTTPMetadataResolver resolver;

	@Autowired
	private RoleCatalogueService roleCatalogueService;
	
	@Autowired
	private CommonConfiguration commonConfig;

	@Autowired
	private KombitServiceProviderConfigV2 kombitConfig;
	
	@Autowired
	private KombitSubSystemService subsystemService;
	
	@Autowired
	private AdvancedRuleService advancedRuleService;

	@Autowired
	private SettingService settingService;
	
	@Override
	public boolean nemLogInBrokerEnabled() {
		return false;
	}

	@Override
	public EntityDescriptor getMetadata() throws ResponderException, RequesterException {
		if (resolver == null || !resolver.isInitialized()) {
			resolver = getMetadataResolver(kombitConfig.getEntityId(), getMetadataUrl());
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
		criteriaSet.add(new EntityIdCriterion(kombitConfig.getEntityId()));

		try {
			return resolver.resolveSingle(criteriaSet);
		}
		catch (ResolverException ex) {
			throw new ResponderException("Konfigureret 'entityID' ikke fundet i metadata", ex);
		}
	}

	@Override
	public String getNameId(Person person) {
		return "C=DK,O=" + commonConfig.getCustomer().getCvr() + ",CN=" + person.getAliasWithFallbackToName() + ",Serial=" + person.getUuid();
	}

	@Override
	public Map<String, Object> getAttributes(LoginRequest loginRequest, Person person, boolean includeDuplicates) {
		Map<String, Object> map = new HashMap<>();

		if (commonConfig.getKombit().isOiosaml2()) {
			map.put("dk:gov:saml:attribute:SpecVer", "DK-SAML-2.0");
			map.put("dk:gov:saml:attribute:KombitSpecVer", "1.0");
			map.put("dk:gov:saml:attribute:CvrNumberIdentifier", commonConfig.getCustomer().getCvr());
			map.put("dk:gov:saml:attribute:AssuranceLevel", commonConfig.getKombit().getAssuranceLevel());
			
			// needed for SEB
			map.put("dk:gov:saml:attribute:CprNumberIdentifier", person.getCpr());
		}
		else {
			map.put("dk:gov:saml:attribute:KombitSpecVer", "2.0");
			map.put("https://data.gov.dk/model/core/specVersion", "OIO-SAML-3.0");
			map.put("https://data.gov.dk/model/core/eid/professional/cvr", commonConfig.getCustomer().getCvr());			
		
			if (StringUtils.hasLength(person.getEmail())) {
				map.put("https://data.gov.dk/model/core/eid/email", person.getEmail());
			}
			
			// we could probably do better, but this allows us some backwards compatibility for non-NSIS users
			if (!person.hasActivatedNSISUser()) {
				map.put("dk:gov:saml:attribute:AssuranceLevel", commonConfig.getKombit().getAssuranceLevel());
			}

			// persistent identifier attribute and cpr (KOMBIT will forward this to SEB who needs it for NL3 mapping)
			if (StringUtils.hasLength(person.getNemloginUserUuid())) {
				map.put("https://data.gov.dk/model/core/eid/professional/uuid/persistent", "urn:uuid:" + person.getNemloginUserUuid());
			}
			
			// needed for SEB
			map.put("dk:gov:saml:attribute:CprNumberIdentifier", person.getCpr());
		}

		String lookupIdentifier = "KOMBIT";
		String entityId = getEntityIdFromAuthnRequest(loginRequest.getAuthnRequest());
		if (entityId != null) {
			KombitSubsystem subsystem = getSubsystem(entityId);
			if (StringUtils.hasLength(subsystem.getOS2rollekatalogIdentifier())) {
				lookupIdentifier = subsystem.getOS2rollekatalogIdentifier();
			}
		}

		String oiobpp = null;
		if (commonConfig.getRoleCatalogue().isEnabled() && commonConfig.getRoleCatalogue().isKombitRolesEnabled()) {
			oiobpp = roleCatalogueService.getOIOBPP(person, lookupIdentifier);
		}
		else {
			oiobpp = generateFromKombitJfr(person, commonConfig.getKombit().isConvertDashToUnderscore());
		}
		
		if (oiobpp != null) {
			if (commonConfig.getKombit().isOiosaml2()) {
				map.put("dk:gov:saml:attribute:Privileges_intermediate", oiobpp);
			}
			else {
				map.put("https://data.gov.dk/model/core/eid/privilegesIntermediate", oiobpp);
			}
		}
		
		if (person.getKombitAttributes() != null && person.getKombitAttributes().size() > 0) {
			for (String key : person.getKombitAttributes().keySet()) {
				map.put(key, person.getKombitAttributes().get(key));
			}
		}
		
		// hackish - should be implemented in a much saner/prettier way, but quickfix for now
		if (commonConfig.getKombit().getExtraKombitClaims() != null && StringUtils.hasLength(commonConfig.getKombit().getKombitDomain())) {
			for (String attribute : commonConfig.getKombit().getExtraKombitClaims().keySet()) {
				String field = commonConfig.getKombit().getExtraKombitClaims().get(attribute);
				
				if (StringUtils.hasLength(field)) {
		            String value = advancedRuleService.lookupField(person, field);
	
		            if (StringUtils.hasLength(value)) {
		                map.put("http://" + commonConfig.getKombit().getKombitDomain() + "/" + attribute + "/1/parametric", value);
		            }
				}
			}
		}

		return map;
	}

	@Override
	public boolean mfaRequired(LoginRequest loginRequest, Domain domain, boolean trustedIP) {
		
		// always check NSIS requirements first
    	switch (nsisLevelRequired(loginRequest)) {
			case HIGH:
	    	case SUBSTANTIAL:
	    		return true;
	    	case LOW:
	    	case NONE:
	    		break;
		}
		
    	// then check for any NIST requirements
    	if (inspectAuthnRequestForMfaRequirement(loginRequest.getAuthnRequest())) {
    		return true;
		}

    	// finally check for any overrides on kombit subsystems (can only be used to increase the security requirements)
		String entityId = getEntityIdFromAuthnRequest(loginRequest.getAuthnRequest());
		if (entityId != null) {
			KombitSubsystem subSystem = getSubsystem(entityId);

			if (subSystem != null) {
				switch (subSystem.getForceMfaRequired()) {
					case ALWAYS:
						return true;
					case ONLY_FOR_UNKNOWN_NETWORKS:
						if (!trustedIP) {
							return true;
						}
						break;
					default:
						// we do not step-down to never, so both are treated as depends
						break;
				}
			}
		}

    	// no requirements for MFA, just return false
    	return false;
	}
	
	// this is the KOMBIT variant of this function (we have a different one in SqlServiceProvider)
    private boolean inspectAuthnRequestForMfaRequirement(AuthnRequest authnRequest) {
        RequestedAuthnContext requestedAuthnContext = authnRequest.getRequestedAuthnContext();
        if (requestedAuthnContext != null && requestedAuthnContext.getAuthnContextClassRefs() != null) {
            for (AuthnContextClassRef authnContextClassRef : requestedAuthnContext.getAuthnContextClassRefs()) {
            	
                if ("urn:dk:gov:saml:attribute:AssuranceLevel:3".equals(authnContextClassRef.getAuthnContextClassRef())) {
                    return true;
                }
                
            	// fallback - if the IdP is misconfigured as "AD FS" this is what we get
                else if ("http://schemas.microsoft.com/claims/multipleauthn".equals(authnContextClassRef.getAuthnContextClassRef())) {
                    return true;
                }
            }
        }

        return false;
	}

	@Override
	public NSISLevel nsisLevelRequired(LoginRequest loginRequest) {
		AuthnRequest authnRequest = loginRequest.getAuthnRequest();

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
		String entityId = getEntityIdFromAuthnRequest(authnRequest);
		if (entityId != null) {
			KombitSubsystem subSystem = getSubsystem(entityId);
			if (subSystem != null) {
				configuredLevel = subSystem.getMinNsisLevel();
			}
		}

        // return the max of configuredLevel and requestedLevel
        ArrayList<NSISLevel> levels = new ArrayList<>();
        levels.add(requestedLevel);
        levels.add(configuredLevel);

        return Collections.max(levels, Comparator.comparingInt(NSISLevel::getLevel));
	}

	@Override
	public boolean preferNemId() {
		return kombitConfig.isPreferNemid();
	}

	@Override
	public String getEntityId() {
		return kombitConfig.getEntityId();
	}
	
	@Override
	public List<String> getEntityIds() {
		return Collections.singletonList(kombitConfig.getEntityId());
	}

	@Override
	public String getName(LoginRequest loginRequest) {
		if (loginRequest == null) {
			return kombitConfig.getName();
		}

		String entityId = getEntityIdFromAuthnRequest(loginRequest.getAuthnRequest());
		if (entityId != null) {
			KombitSubsystem subSystem = getSubsystem(entityId);
			if (StringUtils.hasLength(subSystem.getName())) {
				return subSystem.getName();
			}
			
			return subSystem.getEntityId();
		}

		return kombitConfig.getName();
	}

	@Override
	public RequirementCheckResult personMeetsRequirements(Person person) {
		return RequirementCheckResult.OK;
	}

	@Override
	public boolean encryptAssertions() {
		return kombitConfig.isEncryptAssertions();
	}

	@Override
	public String getNameIdFormat() {
		return kombitConfig.getNameIdFormat().value;
	}

	public String getMetadataUrl() {
		return kombitConfig.getMetadataUrl();
	}

	@Override
	public boolean enabled() {
		return kombitConfig.isEnabled();
	}

	@Override
	public Protocol getProtocol() {
		return kombitConfig.getProtocol();
	}
	
	private KombitSubsystem getSubsystem(String entityId) {
		KombitSubsystem subsystem = subsystemService.findByEntityId(entityId);
		if (subsystem == null) {
			subsystem = new KombitSubsystem();
			subsystem.setEntityId(entityId);
			subsystem.setMinNsisLevel(NSISLevel.NONE);

			ForceMFARequired mfaRequired = ForceMFARequired.DEPENDS;
			String mfaSetting = null;
			try {
				mfaSetting = settingService.getString(SettingKey.KOMBIT_DEFAULT_MFA);
				mfaRequired = ForceMFARequired.valueOf(mfaSetting);
			}
			catch (Exception ex) {
				log.warn("Failed to parse " + mfaSetting, ex);
			}

			subsystem.setForceMfaRequired(mfaRequired);

			subsystem = subsystemService.save(subsystem);
		}
		
		return subsystem;
	}
	
	private String getEntityIdFromAuthnRequest(AuthnRequest authnRequest) {
		if (authnRequest != null && authnRequest.getScoping() != null &&
			authnRequest.getScoping().getRequesterIDs() != null &&
			authnRequest.getScoping().getRequesterIDs().size() > 0 &&
			StringUtils.hasLength(authnRequest.getScoping().getRequesterIDs().get(0).getRequesterID())) {

			return authnRequest.getScoping().getRequesterIDs().get(0).getRequesterID();
		}
		
		return null;
	}

	private String generateFromKombitJfr(Person person, boolean convertDashToUnderscore) {
		StringBuilder builder = new StringBuilder();
		
		builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		builder.append("<bpp:PrivilegeList xmlns:bpp=\"http://itst.dk/oiosaml/basic_privilege_profile\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">");

		for (KombitJfr kombitJfr : person.getKombitJfrs()) {
			String identifier = kombitJfr.getIdentifier();
			if (convertDashToUnderscore) {
				identifier = identifier.replace("-", "_");
			}

			builder.append("<PrivilegeGroup Scope=\"urn:dk:gov:saml:cvrNumberIdentifier:" + kombitJfr.getCvr() + "\">");
			builder.append("<Privilege>" + identifier + "</Privilege>");					
			builder.append("</PrivilegeGroup>");
		}
		
		builder.append("</bpp:PrivilegeList>");
		
		String oiobpp = builder.toString();
		
		return Base64.getEncoder().encodeToString(oiobpp.getBytes(Charset.forName("UTF-8")));
	}

	// always return an NSIS level if available
	@Override
	public boolean supportsNsisLoaClaim() {
		return commonConfig.getKombit().isNsis();
	}
	
	@Override
	public boolean preferNIST() {
		return kombitConfig.isPreferNIST();
	}
	
	@Override
	public boolean requireOiosaml3Profile() {
		return true;
	}

	@Override
	public Long getPasswordExpiry() {
		return null;
	}

	@Override
	public Long getMfaExpiry() {
		return null;
	}
}
