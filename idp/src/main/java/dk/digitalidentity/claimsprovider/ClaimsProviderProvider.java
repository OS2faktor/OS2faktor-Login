package dk.digitalidentity.claimsprovider;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.config.modules.NonNsisIdPConfiguration;
import dk.digitalidentity.controller.dto.LoginRequest;
import dk.digitalidentity.samlmodule.model.IdentityProvider;
import dk.digitalidentity.samlmodule.model.SamlIdentityProviderProvider;
import dk.digitalidentity.security.IdentityProviderWithName;
import dk.digitalidentity.service.SessionHelper;
import dk.digitalidentity.service.serviceprovider.ServiceProvider;
import dk.digitalidentity.service.serviceprovider.ServiceProviderFactory;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ClaimsProviderProvider implements SamlIdentityProviderProvider {
	private List<IdentityProvider> identityProviders = null;
	
	@Autowired
	private OS2faktorConfiguration configuration;
	
	@Autowired
	private SessionHelper sessionHelper;

	@Autowired
	private ServiceProviderFactory serviceProviderFactory;

	@Override
	public List<IdentityProvider> getIdentityProviders() {
		if (identityProviders != null) {
			return identityProviders;
		}
		
		identityProviders = new ArrayList<>();
		
		if (configuration.getClaimsProvider().isStilEnabled()) {
			IdentityProviderWithName stilProvider = new IdentityProviderWithName();
			stilProvider.setContextClassRefEnabled(false);
			stilProvider.setRequirePersonProfile(false);
			stilProvider.setEntityId(configuration.getClaimsProvider().getStilEntityId());
			stilProvider.setMetadata(configuration.getClaimsProvider().getStilMetadata());
			stilProvider.setName("STIL UniLogin");
			identityProviders.add(stilProvider);
		}
		
		if (configuration.getClaimsProvider().isMitIdEnabed()) {
			IdentityProviderWithName nl3Provider = new IdentityProviderWithName();
			nl3Provider.setContextClassRefEnabled(true);
			nl3Provider.setEntityId(configuration.getClaimsProvider().getMitIdEntityId());
			nl3Provider.setMetadata(configuration.getClaimsProvider().getMitIdMetadata());
			nl3Provider.setName("MitID");

			identityProviders.add(nl3Provider);
		}

		if (configuration.getClaimsProvider().isNonNsisIdPEnabled()) {
			for (NonNsisIdPConfiguration idpConfiguration : configuration.getClaimsProvider().getNonNsisIdPs()) {
				IdentityProviderWithName nonNsisProvider = new IdentityProviderWithName();
				nonNsisProvider.setContextClassRefEnabled(false);
				nonNsisProvider.setRequirePersonProfile(false);
				nonNsisProvider.setPassive(idpConfiguration.isPassive());
				nonNsisProvider.setEntityId(idpConfiguration.getEntityId());
				nonNsisProvider.setMetadata(idpConfiguration.getMetadata());
				nonNsisProvider.setName(idpConfiguration.getName());
	
				identityProviders.add(nonNsisProvider);
			}
		}

		return identityProviders;
	}

	@Override
	public IdentityProvider getByEntityId(String entityId) {
		if (identityProviders != null) {
			for (IdentityProvider identityProvider : identityProviders) {
				if (Objects.equals(identityProvider.getEntityId(), entityId)) {
					return identityProvider;
				}
			}
		}

		return null;
	}
	
	public List<NonNsisIdPConfiguration> getIdpsForServiceProvider() {
		List<NonNsisIdPConfiguration> result = new ArrayList<>();

		LoginRequest loginRequest = null;
		ServiceProvider serviceProvider = null;
		try {
			loginRequest = sessionHelper.getLoginRequest();
			if (loginRequest != null) {
				serviceProvider = serviceProviderFactory.getServiceProvider(loginRequest.getServiceProviderId());
			}
		}
		catch (Exception ex) {
			log.warn("Failed to get an IdP whitelist", ex);
		}

		for (NonNsisIdPConfiguration idpConfiguration : configuration.getClaimsProvider().getNonNsisIdPs()) {
			if (serviceProvider == null ||
				serviceProvider.getNsisIdPLoginFilter(loginRequest) == null ||
				serviceProvider.getNsisIdPLoginFilter(loginRequest).size() == 0 ||
				serviceProvider.getNsisIdPLoginFilter(loginRequest).contains(idpConfiguration.getName())) {

				result.add(idpConfiguration);
			}
		}
		
		return result;
	}
}
