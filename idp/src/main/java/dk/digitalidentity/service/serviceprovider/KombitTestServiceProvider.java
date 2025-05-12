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

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.KombitJfr;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.dao.model.enums.Protocol;
import dk.digitalidentity.common.dao.model.enums.RequirementCheckResult;
import dk.digitalidentity.common.dao.model.enums.SettingKey;
import dk.digitalidentity.common.service.SettingService;
import dk.digitalidentity.common.service.rolecatalogue.RoleCatalogueService;
import dk.digitalidentity.common.serviceprovider.KombitTestServiceProviderConfig;
import dk.digitalidentity.controller.dto.LoginRequest;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import lombok.SneakyThrows;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import net.shibboleth.utilities.java.support.resolver.ResolverException;

@Component
public class KombitTestServiceProvider extends ServiceProvider {
	private HTTPMetadataResolver resolver;

	@Autowired
	private RoleCatalogueService roleCatalogueService;
	
	@Autowired
	private CommonConfiguration commonConfig;

	@Autowired
	private KombitTestServiceProviderConfig kombitConfig;

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

	@Override
	public String getNameId(Person person) {
		return "C=DK,O=" + commonConfig.getCustomer().getCvr() + ",CN=" + person.getName() + ",Serial=" + person.getUuid();
	}

	@SneakyThrows
	@Override
	public Map<String, Object> getAttributes(LoginRequest loginRequest, Person person, boolean includeDuplicates) {
		Map<String, Object> map = new HashMap<>();

		map.put("dk:gov:saml:attribute:SpecVer", "DK-SAML-2.0");
		map.put("dk:gov:saml:attribute:KombitSpecVer", "1.0");
		map.put("dk:gov:saml:attribute:CvrNumberIdentifier", commonConfig.getCustomer().getCvr());
		map.put("dk:gov:saml:attribute:AssuranceLevel", commonConfig.getKombit().getAssuranceLevel());

		String lookupIdentifier = "KOMBITTEST";
		String oiobpp = null;
		if (commonConfig.getRoleCatalogue().isEnabled()) {
			oiobpp = roleCatalogueService.getOIOBPP(person, lookupIdentifier);
		}
		else {
			oiobpp = generateFromKombitJfr(person, commonConfig.getKombit().isConvertDashToUnderscore());
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
	public boolean mfaRequired(LoginRequest loginRequest, Domain domain, boolean trustedIP) {
		return false;
	}

	@Override
	public NSISLevel nsisLevelRequired(LoginRequest loginRequest) {
        return NSISLevel.NONE;
	}

	@Override
	public boolean preferNemId() {
		return kombitConfig.isPreferNemid();
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
	public List<String> getEntityIds() {
		return Collections.singletonList(kombitConfig.getEntityId());
	}

	@Override
	public String getName(LoginRequest loginRequest) {
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
		return kombitConfig.isPreferNIST();
	}

	@Override
	public boolean requireOiosaml3Profile() {
		return false;
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
