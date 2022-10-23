package dk.digitalidentity.service.serviceprovider;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
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
import org.springframework.util.StringUtils;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.KombitJfr;
import dk.digitalidentity.common.dao.model.KombitSubsystem;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.service.KombitSubSystemService;
import dk.digitalidentity.common.service.RoleCatalogueService;
import dk.digitalidentity.common.serviceprovider.KombitServiceProviderConfig;
import dk.digitalidentity.util.Constants;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import net.shibboleth.utilities.java.support.resolver.ResolverException;

@Component
public class KombitServiceProvider extends ServiceProvider {
	private HTTPMetadataResolver resolver;

	@Autowired
	private RoleCatalogueService roleCatalogueService;
	
	@Autowired
	private CommonConfiguration commonConfig;

	@Autowired
	private KombitServiceProviderConfig kombitConfig;
	
	@Autowired
	private KombitSubSystemService subsystemService;
	
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
		return "C=DK,O=" + commonConfig.getCustomer().getCvr() + ",CN=" + person.getName() + ",Serial=" + person.getUuid();
	}

	@Override
	public Map<String, Object> getAttributes(AuthnRequest authnRequest, Person person) {
		Map<String, Object> map = new HashMap<>();

		map.put("dk:gov:saml:attribute:SpecVer", "DK-SAML-2.0");
		map.put("dk:gov:saml:attribute:KombitSpecVer", "1.0");
		map.put("dk:gov:saml:attribute:CvrNumberIdentifier", commonConfig.getCustomer().getCvr());
		map.put("dk:gov:saml:attribute:AssuranceLevel", commonConfig.getKombit().getAssuranceLevel());

		String lookupIdentifier = "KOMBIT";
		String entityId = getEntityIdFromAuthnRequest(authnRequest);
		if (entityId != null) {
			KombitSubsystem subsystem = getSubsystem(entityId);
			if (StringUtils.hasLength(subsystem.getOS2rollekatalogIdentifier())) {
				lookupIdentifier = subsystem.getOS2rollekatalogIdentifier();
			}
		}

		String oiobpp = null;
		switch (commonConfig.getKombit().getRoleSource()) {
			case JFR_GROUPS:
				oiobpp = generateFromKombitJfr(person, commonConfig.getKombit().isConvertDashToUnderscore());
				break;
			case OS2ROLLEKATALOG:
				oiobpp = roleCatalogueService.getOIOBPP(person, lookupIdentifier);
				break;
		}
		
		if (oiobpp != null) {
			map.put("dk:gov:saml:attribute:Privileges_intermediate", oiobpp);
		}

		if (person.getKombitAttributes() != null && person.getKombitAttributes().size() > 0) {
			for (String key : person.getKombitAttributes().keySet()) {
				map.put(key, person.getKombitAttributes().get(key));
			}
		}

		return map;
	}

	@Override
	public boolean mfaRequired(AuthnRequest authnRequest) {
		// a configured "always require" on the subsystem overrides any requested level
		String entityId = getEntityIdFromAuthnRequest(authnRequest);
		if (entityId != null) {
			KombitSubsystem subSystem = getSubsystem(entityId);

			if (subSystem != null && subSystem.isAlwaysRequireMfa()) {
				return true;
			}
		}
		
		// otherwise check the requested level
    	switch (nsisLevelRequired(authnRequest)) {
			case HIGH:
	    	case SUBSTANTIAL:
	    		return true;
			default:
				return false;
		}
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
		return kombitConfig.preferNemId();
	}

	@Override
	public boolean nemLogInBrokerEnabled() {
		return false;
	}

	@Override
	public String getEntityId() {
		return kombitConfig.getEntityId();
	}

	@Override
	public String getName(AuthnRequest authnRequest) {
		String entityId = getEntityIdFromAuthnRequest(authnRequest);
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
		return kombitConfig.encryptAssertions();
	}
	
	@Override
	public String getNameIdFormat() {
		return kombitConfig.getNameIdFormat();
	}

	public String getMetadataUrl() {
		return kombitConfig.getMetadataUrl();
	}

	@Override
	public boolean enabled() {
		return kombitConfig.enabled();
	}

	@Override
	public String getProtocol() {
		return kombitConfig.getProtocol();
	}
	
	private KombitSubsystem getSubsystem(String entityId) {
		KombitSubsystem subsystem = subsystemService.findByEntityId(entityId);
		if (subsystem == null) {
			subsystem = new KombitSubsystem();
			subsystem.setEntityId(entityId);
			subsystem.setMinNsisLevel(NSISLevel.NONE);

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

	@Override
	public boolean supportsNsisLoaClaim() {
		return false;
	}
	
	@Override
	public boolean preferNIST() {
		return kombitConfig.preferNIST();
	}
}
