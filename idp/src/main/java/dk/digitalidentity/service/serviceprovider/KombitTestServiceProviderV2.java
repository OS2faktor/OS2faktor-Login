package dk.digitalidentity.service.serviceprovider;

import java.nio.charset.Charset;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.saml.metadata.resolver.impl.HTTPMetadataResolver;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.KombitJfr;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.SettingKey;
import dk.digitalidentity.common.service.SettingService;
import dk.digitalidentity.common.service.rolecatalogue.RoleCatalogueService;
import dk.digitalidentity.common.serviceprovider.KombitTestServiceProviderConfigV2;
import dk.digitalidentity.controller.dto.LoginRequest;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import lombok.SneakyThrows;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import net.shibboleth.utilities.java.support.resolver.ResolverException;

@Component
public class KombitTestServiceProviderV2 extends KombitServiceProviderV2 {
	private HTTPMetadataResolver resolver;

	@Autowired
	private RoleCatalogueService roleCatalogueService;

	@Autowired
	private KombitTestServiceProviderConfigV2 kombitConfig;

	@Autowired
	private CommonConfiguration commonConfig;

	@Autowired
	private SettingService settingService;

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
	
	@SneakyThrows
	@Override
	public Map<String, Object> getAttributes(LoginRequest loginRequest, Person person, boolean includeDuplicates) {
		Map<String, Object> map = new HashMap<>();

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
		
		// persistent identifier attribute (KOMBIT will forward this to SEB who needs it for NL3 mapping)
		if (StringUtils.hasLength(person.getNemloginUserUuid())) {
			map.put("https://data.gov.dk/model/core/eid/professional/uuid/persistent", "urn:uuid:" + person.getNemloginUserUuid());
			map.put("dk:gov:saml:attribute:CprNumberIdentifier", person.getCpr());
		}

		String lookupIdentifier = "KOMBITTEST";
		String oiobpp = null;
		if (commonConfig.getRoleCatalogue().isEnabled() && commonConfig.getRoleCatalogue().isKombitRolesEnabled()) {
			oiobpp = roleCatalogueService.getOIOBPP(person, lookupIdentifier);
		}
		else {
			oiobpp = generateFromKombitJfr(person, commonConfig.getKombit().isConvertDashToUnderscore());
		}
		
		if (oiobpp != null) {
			map.put("https://data.gov.dk/model/core/eid/privilegesIntermediate", oiobpp);
		}
		
		if (person.getKombitAttributes() != null && person.getKombitAttributes().size() > 0) {
			for (String key : person.getKombitAttributes().keySet()) {
				map.put(key, person.getKombitAttributes().get(key));
			}
		}

		return map;
	}

	@Override
	public String getEntityId() {
		return kombitConfig.getEntityId();
	}
		
	@Override
	public List<String> getEntityIds() {
		return Collections.singletonList(kombitConfig.getEntityId());
	}

	// we don't want to mess up our production data with strange test-SP names,
	// so we just map them all to the context handler name
	@Override
	public String getName(LoginRequest loginRequest) {
		return kombitConfig.getName();
	}

	public String getMetadataUrl() {
		return kombitConfig.getMetadataUrl();
	}
	
	@Override
	public boolean preferNIST() {
		return kombitConfig.isPreferNIST();
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
	public Long getPasswordExpiry() {
		if (settingService.getBoolean(SettingKey.KOMBIT_HAS_CUSTOM_EXPIRY)) {
			return settingService.getLong(SettingKey.KOMBIT_PASSWORD_EXPIRY);
		}

		return null;
	}

	@Override
	public Long getMfaExpiry() {
		if (settingService.getBoolean(SettingKey.KOMBIT_HAS_CUSTOM_EXPIRY)) {
			return settingService.getLong(SettingKey.KOMBIT_MFA_EXPIRY);
		}
		
		return null;
	}
}
