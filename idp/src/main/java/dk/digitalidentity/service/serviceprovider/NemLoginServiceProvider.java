package dk.digitalidentity.service.serviceprovider;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.saml.metadata.resolver.impl.HTTPMetadataResolver;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import dk.digitalidentity.common.config.CommonConfiguration;
import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.dao.model.enums.Protocol;
import dk.digitalidentity.common.dao.model.enums.RequirementCheckResult;
import dk.digitalidentity.common.serviceprovider.NemLoginServiceProviderConfig;
import dk.digitalidentity.controller.dto.LoginRequest;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import lombok.SneakyThrows;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import net.shibboleth.utilities.java.support.resolver.ResolverException;

@Component
public class NemLoginServiceProvider extends ServiceProvider {
	public static final String SP_NAME = "NemLog-in";
	private HTTPMetadataResolver resolver;

	@Autowired
	private CommonConfiguration commonConfig;

	@Autowired
	private NemLoginServiceProviderConfig nemloginConfig;

	@Override
	public EntityDescriptor getMetadata() throws ResponderException, RequesterException {
		if (resolver == null || !resolver.isInitialized()) {
			resolver = getMetadataResolver(nemloginConfig.getEntityId(), getMetadataUrl());
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
		criteriaSet.add(new EntityIdCriterion(nemloginConfig.getEntityId()));

		try {
			return resolver.resolveSingle(criteriaSet);
		}
		catch (ResolverException ex) {
			throw new ResponderException("Konfigureret 'entityID' ikke fundet i metadata", ex);
		}
	}

	@Override
	public String getNameId(Person person) {
		return person.getSamaccountName() + person.getDomain().getNemLoginDomain();
	}

	@SneakyThrows
	@Override
	public Map<String, Object> getAttributes(LoginRequest loginRequest, Person person, boolean includeDuplicates) {
		Map<String, Object> map = new HashMap<>();

		map.put("https://data.gov.dk/model/core/specVersion", "OIO-SAML-3.0");
		map.put("https://data.gov.dk/model/core/eid/professional/orgName", commonConfig.getNemLoginTU().getOrgName());
		map.put("https://data.gov.dk/model/core/eid/professional/cvr", commonConfig.getCustomer().getCvr());

		/*
		if (commonConfig.getRoleCatalogue().isEnabled()) {
			String oiobpp = roleCatalogueService.getNemLoginOIOBPP(person);		
			if (oiobpp != null) {
				map.put("https://data.gov.dk/model/core/eid/privilegesIntermediate", oiobpp);
			}
		}
		*/

		return map;
	}

	@Override
	public boolean mfaRequired(LoginRequest loginRequest, Domain domain, boolean trustedIP) {
    	switch (nsisLevelRequired(loginRequest)) {
			case HIGH:
	    	case SUBSTANTIAL:
	    		return true;
			default:
				return false;
		}
	}

	@Override
	public NSISLevel nsisLevelRequired(LoginRequest loginRequest) {
        return NSISLevel.SUBSTANTIAL;
	}

	@Override
	public boolean preferNemId() {
		return nemloginConfig.isPreferNemid();
	}

	@Override
	public boolean nemLogInBrokerEnabled() {
		return false;
	}

	@Override
	public String getEntityId() {
		return nemloginConfig.getEntityId();
	}
	
	@Override
	public List<String> getEntityIds() {
		return Collections.singletonList(nemloginConfig.getEntityId());
	}

	@Override
	public String getName(LoginRequest loginRequest) {
		if (loginRequest == null) {
			return SP_NAME;
		}

		String entityId = getEntityIdFromAuthnRequest(loginRequest.getAuthnRequest());
		
		if (entityId != null) {
			return SP_NAME + " (" + entityId + ")"; 
		}

		return SP_NAME;
	}

	@Override
	public RequirementCheckResult personMeetsRequirements(Person person) {
		return RequirementCheckResult.OK;
	}

	@Override
	public boolean encryptAssertions() {
		return nemloginConfig.isEncryptAssertions();
	}
	
	@Override
	public String getNameIdFormat() {
		return nemloginConfig.getNameIdFormat().value;
	}

	public String getMetadataUrl() {
		return nemloginConfig.getMetadataUrl();
	}

	@Override
	public boolean enabled() {
		return nemloginConfig.isEnabled();
	}

	@Override
	public Protocol getProtocol() {
		return nemloginConfig.getProtocol();
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

	@Override
	public boolean supportsNsisLoaClaim() {
		return true;
	}
	
	@Override
	public boolean preferNIST() {
		return nemloginConfig.isPreferNIST();
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
