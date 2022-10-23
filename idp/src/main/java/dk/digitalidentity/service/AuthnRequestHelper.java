package dk.digitalidentity.service;

import dk.digitalidentity.service.serviceprovider.ServiceProvider;
import dk.digitalidentity.service.serviceprovider.ServiceProviderFactory;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthnRequestHelper {

	@Autowired
	private ServiceProviderFactory serviceProviderFactory;

	public String getConsumerEndpoint(AuthnRequest authnRequest) throws ResponderException, RequesterException  {
		if (authnRequest == null) {
			throw new ResponderException("Kunne ikke finde AssertionConsumerServiceURL fordi AuthnRequest var NULL");
		}
		String assertionConsumerServiceURL = authnRequest.getAssertionConsumerServiceURL();

		if (!StringUtils.hasLength(assertionConsumerServiceURL)) {
			ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(authnRequest);
			assertionConsumerServiceURL = serviceProvider.getMetadata().getSPSSODescriptor(SAMLConstants.SAML20P_NS).getDefaultAssertionConsumerService().getLocation();
		}

		return assertionConsumerServiceURL;
	}
}
