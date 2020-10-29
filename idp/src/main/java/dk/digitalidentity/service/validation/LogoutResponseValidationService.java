package dk.digitalidentity.service.validation;

import java.security.PublicKey;

import javax.servlet.http.HttpServletRequest;

import org.opensaml.messaging.context.MessageContext;
import org.opensaml.messaging.handler.MessageHandlerException;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.common.binding.security.impl.MessageLifetimeSecurityHandler;
import org.opensaml.saml.common.binding.security.impl.ReceivedEndpointSecurityHandler;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.LogoutRequest;
import org.opensaml.saml.saml2.core.LogoutResponse;
import org.springframework.stereotype.Service;

import dk.digitalidentity.util.ResponderException;
import lombok.extern.slf4j.Slf4j;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;

@Slf4j
@Service
public class LogoutResponseValidationService {

	public void validate(HttpServletRequest httpServletRequest, MessageContext<SAMLObject> messageContext, String metadataEntityID, PublicKey publicKey, LogoutRequest logoutRequest) throws ResponderException {
		log.debug("Started validation of LogoutResponse");

		LogoutResponse logoutResponse = (LogoutResponse) messageContext.getMessage();
		if (logoutResponse == null) {
			throw new ResponderException("Request indeholdte ikke en logout svar (LogoutResponse)");
		}

		validateDestination(httpServletRequest, messageContext);
		validateLifeTime(messageContext);
		validateInResponseTo(logoutResponse, logoutRequest);
		validateIssuer(logoutResponse, metadataEntityID);

		log.debug("Completed validation of LogoutResponse");
	}

	@SuppressWarnings("unchecked")
	private void validateDestination(HttpServletRequest httpServletRequest, MessageContext<SAMLObject> messageContext) throws ResponderException {
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
			throw new ResponderException("Destination forkert", e);
		}
		finally {
			if (endpointSecurityHandler != null && endpointSecurityHandler.isInitialized() && !endpointSecurityHandler.isDestroyed()) {
				endpointSecurityHandler.destroy();
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void validateLifeTime(MessageContext<SAMLObject> messageContext) throws ResponderException {
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
			throw new ResponderException("Besked 'lifetime' forkert", e);
		}
		finally {
			if (lifetimeHandler != null && lifetimeHandler.isInitialized() && !lifetimeHandler.isDestroyed()) {
				lifetimeHandler.destroy();
			}
		}
	}

	private void validateInResponseTo(LogoutResponse logoutResponse, LogoutRequest logoutRequest) throws ResponderException {
		log.debug("Validating InResponseTo");

		if (!java.util.Objects.equals(logoutRequest.getID(), logoutResponse.getInResponseTo())) {
			throw new ResponderException("Logout svarets 'InResponseTo' id matcher ikke logout forespørgslens id. Forventet id: " + logoutRequest.getID() + " Faktiske id: " + logoutResponse.getInResponseTo());
		}
	}

	private void validateIssuer(LogoutResponse logoutResponse, String metadataEntityID) throws ResponderException {
		log.debug("Validating Issuer");

		Issuer issuer = logoutResponse.getIssuer();
		if (issuer == null) {
			throw new ResponderException("Ingen 'Issuer' fundet på logout svar (LogoutResponse)");
		}

		if (!java.util.Objects.equals(metadataEntityID, issuer.getValue())) {
			throw new ResponderException("'Issuer' matcher ikke tjenesteudbyderen. Forventet: " + metadataEntityID + " Var: " + issuer.getValue());
		}
	}
}
