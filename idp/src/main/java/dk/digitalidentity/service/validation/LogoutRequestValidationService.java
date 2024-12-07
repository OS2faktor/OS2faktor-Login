package dk.digitalidentity.service.validation;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;

import org.opensaml.messaging.context.MessageContext;
import org.opensaml.messaging.handler.MessageHandlerException;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.common.binding.security.impl.MessageLifetimeSecurityHandler;
import org.opensaml.saml.common.binding.security.impl.ReceivedEndpointSecurityHandler;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.LogoutRequest;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.security.credential.UsageType;
import org.opensaml.security.crypto.SigningUtil;
import org.opensaml.security.x509.BasicX509Credential;
import org.opensaml.xmlsec.algorithm.AlgorithmSupport;
import org.opensaml.xmlsec.signature.Signature;
import org.opensaml.xmlsec.signature.support.SignatureValidator;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import dk.digitalidentity.service.serviceprovider.ServiceProvider;
import dk.digitalidentity.util.Constants;
import dk.digitalidentity.util.RequesterException;
import dk.digitalidentity.util.ResponderException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;

@Slf4j
@Service
public class LogoutRequestValidationService {
	public void validate(HttpServletRequest request, MessageContext<SAMLObject> messageContext, EntityDescriptor metadata, ServiceProvider serviceProvider) throws RequesterException, ResponderException {
		log.debug("Started validation of LogoutRequest");

		LogoutRequest logoutRequest = (LogoutRequest) messageContext.getMessage();
		if (logoutRequest == null) {
			throw new RequesterException("Request indeholdte ikke en logout forespørgsel (LogoutRequest)");
		}

		validateDestination(request, messageContext);
		validateLifeTime(messageContext);
		validateIssuer(logoutRequest, metadata.getEntityID());
		if (serviceProvider.validateSignatureOnLogoutRequests()) {
			validateSignature(request, logoutRequest, serviceProvider);
		}
		validateSessionIndex(logoutRequest);

		log.debug("Completed validation of LogoutRequest");
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

	private void validateSessionIndex(LogoutRequest logoutRequest) throws RequesterException {
		log.debug("Validating SessionIndex");

		if (logoutRequest.getSessionIndexes().size() != 1) {
			throw new RequesterException("Kunne ikke finde et enktelt 'sessionIndex' i forespørgslen");
		}
		if (!StringUtils.hasLength(logoutRequest.getSessionIndexes().get(0).getSessionIndex())) {
			throw new RequesterException("'SessionIndex' Er tomt");
		}
	}

	private void validateIssuer(LogoutRequest logoutRequest, String metadataEntityID) throws RequesterException {
		log.debug("Validating Issuer");

		Issuer issuer = logoutRequest.getIssuer();
		if (issuer == null) {
			throw new RequesterException("Ingen 'Issuer' fundet på logout forespørgsel (LogoutRequest)");
		}

		if (!java.util.Objects.equals(metadataEntityID, issuer.getValue())) {
			throw new RequesterException("'Issuer' matcher ikke tjenesteudbyderen. Forventet: " + metadataEntityID + " Var: " + issuer.getValue());
		}
	}

	private void validateSignature(HttpServletRequest request, LogoutRequest logoutRequest, ServiceProvider serviceProvider) throws RequesterException, ResponderException {
		log.debug("Validating Signature");
		
		boolean validSignature = false;

		if (request.getMethod().equals("POST")) {

			// HACK!!! For some reason OpenSAML does not register ID as an ID attribute on LogoutRequest objects :(
			logoutRequest.getDOM().setIdAttribute("ID", true);
			
			Signature signature = logoutRequest.getSignature();
			if (signature == null) {
				throw new RequesterException("Logout forespørgsel (LogoutRequest) har ingen signatur");
			}
			
			List<X509Certificate> signingCerts = serviceProvider.getX509Certificate(UsageType.SIGNING);
			if (signingCerts == null || signingCerts.size() == 0) {
				throw new ResponderException("Unable find certificates to validate signature on LogoutRequest in metadata");
			}

			Exception lastKnownExceptionForLoggingPurposes = null;
			for (X509Certificate signingCert : signingCerts) {
				try {
					SignatureValidator.validate(logoutRequest.getSignature(), new BasicX509Credential(signingCert));
					validSignature = true;
					
					// break for loop we have found a valid signature/credential combo
					break;
				}
				catch (Exception ex) {
					lastKnownExceptionForLoggingPurposes = ex;
				}
			}
			
			if (!validSignature && lastKnownExceptionForLoggingPurposes != null) {
				log.warn("Failed to validate signature", lastKnownExceptionForLoggingPurposes);
			}
		}
		else {
			String queryString = request.getQueryString();
			String signature = request.getParameter("Signature");
			String sigAlg = request.getParameter("SigAlg");

			if (!StringUtils.hasLength(signature)) {
				throw new RequesterException("Logout forespørgsel (LogoutRequest) har ingen signatur");
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
			throw new RequesterException("Logout forespørgsel (LogoutRequest) Signatur forkert");
		}
	}

	private boolean validateSignature(String queryString, String queryParameter, PublicKey publicKey, String signature, String sigAlg) throws RequesterException {
		// Get url string to be verified
		byte[] data = new byte[0];
		data = parseSignedQueryString(queryString, queryParameter).getBytes(StandardCharsets.UTF_8);

		try {
			// Decode signature
			byte[] decodedSignature = Base64.getDecoder().decode(signature);
			String jcaAlgorithmID = AlgorithmSupport.getAlgorithmID(sigAlg);

			return SigningUtil.verify(publicKey, jcaAlgorithmID, decodedSignature, data);
		}
		catch (Exception ex) {
			log.warn("Signatur forkert på logout forespørgsel (LogoutRequest)", ex);
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
