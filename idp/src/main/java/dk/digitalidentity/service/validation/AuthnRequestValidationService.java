package dk.digitalidentity.service.validation;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.opensaml.messaging.context.MessageContext;
import org.opensaml.messaging.handler.MessageHandlerException;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.common.binding.security.impl.MessageLifetimeSecurityHandler;
import org.opensaml.saml.common.binding.security.impl.ReceivedEndpointSecurityHandler;
import org.opensaml.saml.saml2.core.AuthnContextClassRef;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.RequestedAuthnContext;
import org.opensaml.security.credential.UsageType;
import org.opensaml.security.crypto.SigningUtil;
import org.opensaml.security.x509.BasicX509Credential;
import org.opensaml.xmlsec.algorithm.AlgorithmSupport;
import org.opensaml.xmlsec.signature.Signature;
import org.opensaml.xmlsec.signature.support.SignatureException;
import org.opensaml.xmlsec.signature.support.SignatureValidator;
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
		if (!serviceProvider.allowUnsignedAuthnRequests()) {
			validateSignature(httpServletRequest, serviceProvider, authnRequest);
		}
		validateAssertionConsumer(authnRequest, serviceProvider);
		validateIssuer(authnRequest, serviceProvider);
		validateRequirements(authnRequest);

		log.debug("Completed validation of AuthnRequest");
	}

	private void validateRequirements(AuthnRequest authnRequest) throws RequesterException {
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
		if (StringUtils.hasLength(url)) {
			log.debug("Checking AssertionConsumerServiceURL against list of AssertionConsumerServiceURLs in SP metadata");


			/* TODO: kan ikke læse mere end ét endpoint.. so hvad gør vi her?
			SPSSODescriptor spssoDescriptor = serviceProvider.getMetadata().getSPSSODescriptor(SAMLConstants.SAML20P_NS);
			boolean anyMatch = spssoDescriptor.assertionConsumerServices.stream().map(Endpoint::getLocation).anyMatch(s -> s.equals(url));
	
			if (!anyMatch) {
				log.info("Could not find a match from this list: " + String.join(", ", assertionConsumerServices.stream().map(s -> s.getLocation()).collect(Collectors.toSet())));
				throw new RequesterException("Den givne 'AssertionConsumerServiceURL' (" + url + ") matcher ingen af tjenesteudbyderens 'AssertionConsumerServiceURLs'");
			}
			*/
		}
	}

	private void validateIssuer(AuthnRequest authnRequest, ServiceProvider serviceProvider) throws RequesterException, ResponderException {
		log.debug("Validating Issuer");

		Issuer issuer = authnRequest.getIssuer();
		if (issuer == null) {
			throw new RequesterException("Ingen 'Issuer' fundet på login forespørgsel (AuthnRequest)");
		}

		boolean match = false;
		for (String entityId : serviceProvider.getEntityIds()) {
			if (java.util.Objects.equals(entityId, issuer.getValue())) {
				match = true;
			}
		}
		
		if (!match) {
			throw new RequesterException("'Issuer' matcher ikke tjenesteudbyderen (" + serviceProvider.getEntityId() + ")- modtog " + issuer.getValue());
		}
	}
	
	private void validateSignature(HttpServletRequest request, ServiceProvider serviceProvider, AuthnRequest authnRequest) throws RequesterException, ResponderException {
		log.debug("Validating Signature");
		
		boolean validSignature = false;
		
		if (request.getMethod().equals("POST")) {
			Signature signature = authnRequest.getSignature();
			if (signature == null) {
				throw new RequesterException("Login forespørgsel (AuthnRequest) har ingen signatur");
			}

			List<X509Certificate> signingCerts = serviceProvider.getX509Certificate(UsageType.SIGNING);
			if (signingCerts == null || signingCerts.size() == 0) {
				throw new ResponderException("Unable find certificates to validate signature on AuthnRequest in metadata");
			}

			for (X509Certificate signingCert : signingCerts) {
				try {
					SignatureValidator.validate(authnRequest.getSignature(), new BasicX509Credential(signingCert));
					validSignature = true;
					
					// break for loop we have found a valid signature/credential combo
					break;
				}
				catch (SignatureException ignored) {
					; // validate method throws an exception if not valid, we iterate over all of them until we find one valid combo
				}
			}
		}
		else {
			String queryString = request.getQueryString();
			String signature = request.getParameter("Signature");
			String sigAlg = request.getParameter("SigAlg");

			if (!StringUtils.hasLength(signature)) {
				throw new RequesterException("Login forespørgsel (AuthnRequest) har ingen signatur");
			}

			List<PublicKey> signingKeys = serviceProvider.getSigningKeys();
			for (PublicKey signingKey : signingKeys) {
				if (validateSignature(queryString, Constants.SAML_REQUEST, signingKey, signature, sigAlg)) {
					validSignature = true;
					break;
				}
			}
		}
		
		if (!validSignature) {
			throw new RequesterException("Login forespørgsel (AuthnRequest) Signatur forkert");
		}
	}

	private boolean validateSignature(String queryString, String queryParameter, PublicKey publicKey, String signature, String sigAlg) throws RequesterException {
		String jcaAlgorithmID = null;
		byte[] decodedSignature = null;
		byte[] data = new byte[0];
		
		try {
			// get url string to be verified
			data = parseSignedQueryString(queryString, queryParameter).getBytes(StandardCharsets.UTF_8);
	
			// decode signature
			decodedSignature = Base64.getDecoder().decode(signature);
			jcaAlgorithmID = AlgorithmSupport.getAlgorithmID(sigAlg);
		}
		catch (Exception ex) {
			log.warn("Invalid data on signature (AuthnRequest", ex);
			return false;
		}

		try {
			return SigningUtil.verify(publicKey, jcaAlgorithmID, decodedSignature, data);
		}
		catch (Exception ex) {
			log.warn("Signatur forkert på login forespørgsel (AuthnRequest)", ex);
			return false;
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
