package dk.digitalidentity.service.validation;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.Base64;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.opensaml.messaging.context.MessageContext;
import org.opensaml.messaging.handler.MessageHandlerException;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.common.binding.security.impl.MessageLifetimeSecurityHandler;
import org.opensaml.saml.common.binding.security.impl.ReceivedEndpointSecurityHandler;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.core.AuthnContextClassRef;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.RequestedAuthnContext;
import org.opensaml.saml.saml2.metadata.AssertionConsumerService;
import org.opensaml.saml.saml2.metadata.Endpoint;
import org.opensaml.saml.saml2.metadata.SPSSODescriptor;
import org.opensaml.security.SecurityException;
import org.opensaml.security.crypto.SigningUtil;
import org.opensaml.xmlsec.algorithm.AlgorithmSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import dk.digitalidentity.service.serviceprovider.ServiceProvider;
import dk.digitalidentity.service.serviceprovider.ServiceProviderFactory;
import dk.digitalidentity.util.Constants;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import lombok.extern.slf4j.Slf4j;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;

@Slf4j
@Service
public class AuthnRequestValidationService {

	@Autowired
	private ServiceProviderFactory serviceProviderFactory;

	public void validate(HttpServletRequest httpServletRequest, MessageContext<SAMLObject> messageContext) throws RequesterException, ResponderException {
		log.debug("Started validation of AuthnRequest");

		// Get AuthnRequest
		AuthnRequest authnRequest = (AuthnRequest) messageContext.getMessage();
		if (authnRequest == null) {
			throw new RequesterException("Request indeholdte ikke en login forespørgsel (AuthnRequest)");
		}

		ServiceProvider serviceProvider = serviceProviderFactory.getServiceProvider(authnRequest);
		if (serviceProvider == null) {
			throw new ResponderException("Ukendt tjenesteudbyder i login forespørgsel Issuer: " + authnRequest.getIssuer().getValue());
		}

		validateNSISLevel(authnRequest);
		validateDestination(httpServletRequest, messageContext);
		validateLifeTime(messageContext);
		validateSignature(httpServletRequest, serviceProvider);
		validateAssertionConsumer(authnRequest, serviceProvider);
		validateIssuer(authnRequest, serviceProvider);
		validateRequireMents(authnRequest);

		log.debug("Completed validation of AuthnRequest");
	}

	private void validateRequireMents(AuthnRequest authnRequest) throws RequesterException {
		if (authnRequest.isForceAuthn() && authnRequest.isPassive()) {
			throw new RequesterException("Kan ikke både tvinge login og være passiv (både ForceAuthn og isPassive er true)");
		}
	}

	private void validateNSISLevel(AuthnRequest authnRequest) throws ResponderException {
		log.debug("Validating NSIS Level");

		RequestedAuthnContext requestedAuthnContext = authnRequest.getRequestedAuthnContext();
		if (requestedAuthnContext != null && requestedAuthnContext.getAuthnContextClassRefs() != null) {
			for (AuthnContextClassRef authnContextClassRef : requestedAuthnContext.getAuthnContextClassRefs()) {
				String value = authnContextClassRef.getAuthnContextClassRef();
				if (Constants.LEVEL_OF_ASSURANCE_HIGH.equals(value)) {
					throw new ResponderException("NSIS niveau Høj ikke understøttet");
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void validateDestination(HttpServletRequest httpServletRequest, MessageContext<SAMLObject> messageContext) throws ResponderException, RequesterException {
		log.debug("Validating destination");

		ReceivedEndpointSecurityHandler endpointSecurityHandler = null;
		try {
			endpointSecurityHandler = new ReceivedEndpointSecurityHandler();
			endpointSecurityHandler.setHttpServletRequest(httpServletRequest);
			endpointSecurityHandler.initialize();
			endpointSecurityHandler.invoke(messageContext);
		}
		catch (ComponentInitializationException e) {
			throw new ResponderException("Kunne ikke initialisere ReceivedEndpointSecurityHandler", e);
		}
		catch (MessageHandlerException e) {
			throw new RequesterException("Destination forkert", e);
		}
		finally {
			if (endpointSecurityHandler != null && endpointSecurityHandler.isInitialized() && !endpointSecurityHandler.isDestroyed()) {
				endpointSecurityHandler.destroy();
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void validateLifeTime(MessageContext<SAMLObject> messageContext) throws ResponderException, RequesterException {
		log.debug("Validating Lifetime");

		MessageLifetimeSecurityHandler lifetimeHandler = null;
		try {
			lifetimeHandler = new MessageLifetimeSecurityHandler();
			lifetimeHandler.setClockSkew(60 * 5 * 1000);
			lifetimeHandler.initialize();
			lifetimeHandler.invoke(messageContext);
		}
		catch (ComponentInitializationException e) {
			throw new ResponderException("Kunne ikke initialisere MessageLifetimeSecurityHandler", e);
		}
		catch (MessageHandlerException e) {
			throw new RequesterException("Besked 'lifetime' forkert", e);
		}
		finally {
			if (lifetimeHandler != null && lifetimeHandler.isInitialized() && !lifetimeHandler.isDestroyed()) {
				lifetimeHandler.destroy();
			}
		}
	}

	private void validateAssertionConsumer(AuthnRequest authnRequest, ServiceProvider serviceProvider) throws RequesterException, ResponderException {
		log.debug("Validating AssertionConsumerURL");

		String url = authnRequest.getAssertionConsumerServiceURL();
		if (!StringUtils.isEmpty(url)) {
			log.debug("Checking AssertionConsumerServiceURL against list of AssertionConsumerServiceURLs in SP metadata");

			SPSSODescriptor spssoDescriptor = serviceProvider.getMetadata().getSPSSODescriptor(SAMLConstants.SAML20P_NS);

			List<AssertionConsumerService> assertionConsumerServices = spssoDescriptor.getAssertionConsumerServices();
			boolean anyMatch = assertionConsumerServices.stream().map(Endpoint::getLocation).anyMatch(s -> s.equals(url));
	
			if (!anyMatch) {
				throw new RequesterException("Den givne 'AssertionConsumerServiceURL' matcher ingen af tjenesteudbyderens 'AssertionConsumerServiceURLs'");
			}
		}
	}

	private void validateIssuer(AuthnRequest authnRequest, ServiceProvider serviceProvider) throws RequesterException, ResponderException {
		log.debug("Validating Issuer");

		String metadataEntityID = serviceProvider.getEntityId();

		Issuer issuer = authnRequest.getIssuer();
		if (issuer == null) {
			throw new RequesterException("Ingen 'Issuer' fundet på login forespørgsel (AuthnRequest)");
		}

		if (!java.util.Objects.equals(metadataEntityID, issuer.getValue())) {
			throw new RequesterException("'Issuer' matcher ikke tjenesteudbyderen. Forventet: " + metadataEntityID + " Var: " + issuer.getValue());
		}
	}

	private void validateSignature(HttpServletRequest request, ServiceProvider serviceProvider) throws RequesterException, ResponderException {
		log.debug("Validating Signature");

		PublicKey signingKey = serviceProvider.getSigningKey();

		String queryString = request.getQueryString();
		String signature = request.getParameter("Signature");
		String sigAlg = request.getParameter("SigAlg");

		if (!validateSignature(queryString, Constants.SAMLRequest, signingKey, signature, sigAlg)) {
			throw new RequesterException("Login forespørgsel (AuthnRequest) Signatur forkert");
		}
	}

	private boolean validateSignature(String queryString, String queryParameter, PublicKey publicKey, String signature, String sigAlg) throws RequesterException {
		// Get url string to be verified
		byte[] data = new byte[0];
		data = parseSignedQueryString(queryString, queryParameter).getBytes(StandardCharsets.UTF_8);

		// Decode signature
		byte[] decodedSignature = Base64.getDecoder().decode(signature);
		String jcaAlgorithmID = AlgorithmSupport.getAlgorithmID(sigAlg);

		try {
			return SigningUtil.verify(publicKey, jcaAlgorithmID, decodedSignature, data);
		}
		catch (SecurityException e) {
			throw new RequesterException("Signatur forkert på login forespørgsel (AuthnRequest) ", e);
		}
	}

	private String parseSignedQueryString(String queryString, String queryParameter) {
		StringBuilder s = new StringBuilder();

		String samlRequestOrResponse = getParameter(queryParameter, queryString);
		String relayState = getParameter("RelayState", queryString);
		String sigAlg = getParameter("SigAlg", queryString);

		s.append(queryParameter);
		s.append("=");
		s.append(samlRequestOrResponse);

		if (relayState != null) {
			s.append("&");
			s.append("RelayState");
			s.append("=");
			s.append(relayState);
		}

		s.append("&");
		s.append("SigAlg");
		s.append("=");
		s.append(sigAlg);

		return s.toString();
	}

	private String getParameter(String name, String url) {
		String[] parameters = url.split("&");

		for (String parameter : parameters) {
			int pos = parameter.indexOf('=');
			String key = parameter.substring(0, pos);

			if (name.equals(key)) {
				return parameter.substring(pos + 1);
			}
		}

		return null;
	}
}
