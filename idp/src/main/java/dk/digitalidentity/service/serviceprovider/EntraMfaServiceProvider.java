package dk.digitalidentity.service.serviceprovider;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.dao.model.Domain;
import dk.digitalidentity.common.dao.model.Person;
import dk.digitalidentity.common.dao.model.enums.NSISLevel;
import dk.digitalidentity.common.dao.model.enums.Protocol;
import dk.digitalidentity.common.dao.model.enums.RequirementCheckResult;
import dk.digitalidentity.common.serviceprovider.EntraMfaServiceProviderConfig;
import dk.digitalidentity.controller.dto.LoginRequest;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import lombok.SneakyThrows;

@Component
public class EntraMfaServiceProvider extends ServiceProvider {

	@Autowired
	private EntraMfaServiceProviderConfig entraConfig;
	
	@Override
	public EntityDescriptor getMetadata() throws ResponderException, RequesterException {
		return null;
	}

	@Override
	public String getNameId(Person person) {
		return getUpn(person);
	}

	@SneakyThrows
	@Override
	public Map<String, Object> getAttributes(LoginRequest loginRequest, Person person, boolean includeDuplicates) {
		Map<String, Object> map = new HashMap<>();
		
		map.put("upn", getUpn(person));

		return map;
	}

	@SneakyThrows
	@Override
	public boolean mfaRequired(LoginRequest loginRequest, Domain domain, boolean trustedIP) {
		return true;
	}

	@SneakyThrows
	@Override
	public NSISLevel nsisLevelRequired(LoginRequest loginRequest) {
		return NSISLevel.NONE;
	}

	@Override
	public boolean preferNemId() {
		return entraConfig.isPreferNemid();
	}

	@Override
	public boolean nemLogInBrokerEnabled() {
		return false;
	}

	@Override
	public String getEntityId() {
		return entraConfig.getEntityId();
	}
	
	@Override
	public List<String> getEntityIds() {
		return Collections.singletonList(entraConfig.getEntityId());
	}

	@Override
	public String getName(LoginRequest loginRequest) {
		return entraConfig.getName();
	}

	@Override
	public RequirementCheckResult personMeetsRequirements(Person person) {
		return RequirementCheckResult.OK;
	}

	@Override
	public boolean encryptAssertions() {
		return entraConfig.isEncryptAssertions();
	}
	
	@Override
	public String getNameIdFormat() {
		return entraConfig.getNameIdFormat().value;
	}

	public String getMetadataUrl() {
		return entraConfig.getMetadataUrl();
	}

	@Override
	public boolean enabled() {
		return entraConfig.isEnabled();
	}

	@Override
	public Protocol getProtocol() {
		return entraConfig.getProtocol();
	}

	@Override
	public boolean supportsNsisLoaClaim() {
		return false;
	}
	
	@Override
	public boolean preferNIST() {
		return entraConfig.isPreferNIST();
	}

	@Override
	public boolean requireOiosaml3Profile() {
		return false;
	}

	@Override
	public Long getPasswordExpiry() {
		return null;
	}

	@Override
	public Long getMfaExpiry() {
		return null;
	}
	
	// different casing across munis, oh well :)
	private String getUpn(Person person) {
		String upn = person.getAttributes().get("upn");
		if (upn == null) {
			upn = person.getAttributes().get("UPN");
		}
		
		return upn;
	}

	@Override
	public boolean onlyAllowLoginFromKnownNetworks() {
		return entraConfig.isOnlyAllowLoginFromKnownNetworks();
	}
}
