package dk.digitalidentity.service.serviceprovider;

import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import java.util.HashMap;
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
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.service.RoleCatalogueService;
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
	private OS2faktorConfiguration configuration;
	
	@Autowired
	private CommonConfiguration common;

	class ItSystemMetadata {
		String name;
		String uuid;
		
		ItSystemMetadata(String name, String uuid) {
			this.uuid = uuid;
			this.name = name;
		}
	}

	private static final Map<String, ItSystemMetadata> itSystemMap = new HashMap<>();
	static {
		KombitServiceProvider ksp = new KombitServiceProvider();
		
		// TODO: we need to get KOMBIT to expose these through an API, otherwise we have to keep them updated over time
		itSystemMap.put("https://saml.prod-dar.dk/", ksp.new ItSystemMetadata("DAR", "7bffce30-10a8-4808-8157-3a773fc1cdfb"));
		itSystemMap.put("https://www.dubu.dk/sp", ksp.new ItSystemMetadata("DUBU", "38e166c7-f465-48b7-bf6e-46e4ae40fea7"));
		itSystemMap.put("https://saml.test-sitprod-ejf.dk/", ksp.new ItSystemMetadata("Ejerfortegnelsen", "f149ca59-1cf6-4e08-8bfb-20e7ab2ccfa5"));
		itSystemMap.put("https://saml.prod-bbr.dk/", ksp.new ItSystemMetadata("BBR", "22189878-b7c7-44c0-8ef9-ade230844587"));
		itSystemMap.put("https://sapaadvis.dk", ksp.new ItSystemMetadata("SAPA Advis", "d0648cdc-4e99-4879-be07-bdfc8c6e9cee"));
		itSystemMap.put("https://sapaoverblik.dk/", ksp.new ItSystemMetadata("SAPA Overblik", "b75fb26d-2b2d-4d42-a7d5-f06eb7a93067"));
	}
	
	@Override
	public EntityDescriptor getMetadata() throws ResponderException, RequesterException {
		if (resolver == null || !resolver.isInitialized()) {
			resolver = getMetadataResolver(configuration.getKombit().getEntityId(), configuration.getKombit().getMetadataUrl());
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
		criteriaSet.add(new EntityIdCriterion(configuration.getKombit().getEntityId()));

		try {
			return resolver.resolveSingle(criteriaSet);
		}
		catch (ResolverException ex) {
			throw new ResponderException("Konfigureret 'entityID' ikke fundet i metadata", ex);
		}
	}

	@Override
	public String getNameId(Person person) {
		return "C=DK,O=" + common.getCustomer().getCvr() + ",CN=" + person.getName() + ",Serial=" + person.getUuid();
	}

	@Override
	public Map<String, Object> getAttributes(Person person) {
		Map<String, Object> map = new HashMap<>();

		map.put("dk:gov:saml:attribute:SpecVer", "DK-SAML-2.0");
		map.put("dk:gov:saml:attribute:KombitSpecVer", "1.0");
		map.put("dk:gov:saml:attribute:CvrNumberIdentifier", common.getCustomer().getCvr());
		map.put("dk:gov:saml:attribute:AssuranceLevel", configuration.getKombit().getAssuranceLevel());

		// TODO: we need to inspect the AuthnRequest to see what it-system we are looking up roles for
		String oiobpp = roleCatalogueService.getOIOBPP(person, "KOMBIT");
		if (oiobpp != null) {
			map.put("dk:gov:saml:attribute:Privileges_intermediate", oiobpp);
		}

		return map;
	}

	@Override
	public boolean mfaRequired(AuthnRequest authnRequest) {
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

		return NSISLevel.NONE;
	}

	@Override
	public boolean preferNemId() {
		return false;
	}

	@Override
	public String getEntityId() throws RequesterException, ResponderException {
		return configuration.getKombit().getEntityId();
	}

	@Override
	public String getName() {
		return "KOMBIT Context Handler";
	}

	@Override
	public boolean encryptAssertions() {
		return configuration.getKombit().isEncryptAssertion();
	}

	@Override
	public String getNameIdFormat() {
		return "urn:oasis:names:tc:SAML:1.1:nameid-format:X509SubjectName";
	}

	@Override
	public boolean enabled() {
		if (configuration.getKombit() == null || StringUtils.isEmpty(configuration.getKombit().getMetadataUrl())) {
			return false;
		}

		return true;
	}
}
