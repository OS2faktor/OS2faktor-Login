package dk.digitalidentity.claimsprovider;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dk.digitalidentity.config.OS2faktorConfiguration;
import dk.digitalidentity.samlmodule.model.IdentityProvider;
import dk.digitalidentity.samlmodule.model.SamlIdentityProviderProvider;

@Component
public class ClaimsProviderProvider implements SamlIdentityProviderProvider {
	private List<IdentityProvider> identityProviders = null;
	
	@Autowired
	private OS2faktorConfiguration configuration;

	@Override
	public List<IdentityProvider> getIdentityProviders() {
		if (identityProviders != null) {
			return identityProviders;
		}
		
		identityProviders = new ArrayList<>();
		
		if (configuration.getClaimsProvider().isStilEnabled()) {
			IdentityProvider stilProvider = new IdentityProvider();
			stilProvider.setContextClassRefEnabled(false);
			stilProvider.setRequirePersonProfile(false);
			stilProvider.setEntityId(configuration.getClaimsProvider().getStilEntityId());
			stilProvider.setMetadata(configuration.getClaimsProvider().getStilMetadata());
			identityProviders.add(stilProvider);
		}
		
		if (configuration.getClaimsProvider().isMitIdEnabed()) {
			IdentityProvider nl3Provider = new IdentityProvider();
			nl3Provider.setContextClassRefEnabled(true);
			nl3Provider.setEntityId(configuration.getClaimsProvider().getMitIdEntityId());
			nl3Provider.setMetadata(configuration.getClaimsProvider().getMitIdMetadata());

			identityProviders.add(nl3Provider);
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
}
