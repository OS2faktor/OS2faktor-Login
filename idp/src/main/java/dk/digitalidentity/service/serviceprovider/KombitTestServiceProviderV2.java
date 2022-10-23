package dk.digitalidentity.service.serviceprovider;

import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.saml.metadata.resolver.impl.HTTPMetadataResolver;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dk.digitalidentity.common.serviceprovider.KombitTestServiceProviderConfigV2;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import net.shibboleth.utilities.java.support.resolver.ResolverException;

@Component
public class KombitTestServiceProviderV2 extends KombitServiceProviderV2 {
	private HTTPMetadataResolver resolver;

	@Autowired
	private KombitTestServiceProviderConfigV2 kombitConfig;

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
	public String getEntityId() {
		return kombitConfig.getEntityId();
	}

	// we don't want to mess up our production data with strange test-SP names,
	// so we just map them all to the context handler name
	@Override
	public String getName(AuthnRequest authnRequest) {
		return kombitConfig.getName();
	}

	public String getMetadataUrl() {
		return kombitConfig.getMetadataUrl();
	}
	
	@Override
	public boolean preferNIST() {
		return kombitConfig.preferNIST();
	}
}
